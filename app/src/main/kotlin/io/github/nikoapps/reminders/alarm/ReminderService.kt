package io.github.nikoapps.reminders.alarm

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Resources
import android.media.AudioAttributes
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import io.github.nikoapps.reminders.reminders.Outcome
import io.github.nikoapps.reminders.reminders.Played
import io.github.nikoapps.reminders.reminders.ReminderFiles
import io.github.nikoapps.reminders.reminders.RemindersRepository
import io.github.nikoapps.reminders.settings.SettingsRepository
import io.github.nikoapps.reminders.speech.phrasesFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDateTime

/**
 * Plays reminders that came due, one after the other, then stops. Also takes Done from the
 * notification and the reminder screen, and says "Done" in the phone's voice.
 *
 * Android 17 only lets apps play audio from the background in a foreground service, and
 * reminders come due with the app closed, so everything that plays goes through here. Type
 * `specialUse`, as Niko Clock's: `shortService` doesn't count for background audio on Android 17.
 * It never plays from a broadcast receiver.
 */
class ReminderService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val queue = Mutex()
    /** Reminders that come due play on the alarm stream: on silent too, at their own volume. */
    private var alarmPlayer: ReminderPlayer? = null

    /** "Done" is said on the accessibility stream: someone just pressed it. */
    private var voicePlayer: ReminderPlayer? = null

    /** The reminder playing now, so Done for another one doesn't cut it off. Main thread only. */
    private var playingId: String? = null

    /** Done came in for these, maybe while they waited in the queue. They don't play again in this run. */
    private val doneIds = mutableSetOf<String>()
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        // The alarm's own wake window is about 10 seconds; playing can take longer.
        if (wakeLock == null) {
            wakeLock = getSystemService<PowerManager>()
                ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NikoReminders:play")
                ?.apply { acquire(WAKE_LOCK_TIMEOUT_MS) }
        }
        // Stops at once, before anything queued behind it.
        when (intent?.action) {
            ACTION_STOP_SOUND -> alarmPlayer?.stop()
            ACTION_DONE -> intent.getStringExtra(EXTRA_ID)?.let { id ->
                doneIds += id
                if (playingId == id) alarmPlayer?.stop()
            }
        }
        scope.launch {
            try {
                queue.withLock { handle(intent) }
            } finally {
                // Only stops if no newer request came in meanwhile.
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun handle(intent: Intent?) {
        when (intent?.action) {
            ACTION_PLAY -> {
                val ids = intent.getStringArrayExtra(EXTRA_IDS).orEmpty()
                val planned = intent.getStringArrayExtra(EXTRA_PLANNED).orEmpty()
                val last = intent.getBooleanArrayExtra(EXTRA_LAST) ?: BooleanArray(0)
                ids.indices.forEach { i ->
                    val time = planned.getOrNull(i)?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() } ?: return@forEach
                    play(ids[i], time, last.getOrElse(i) { false })
                }
            }
            ACTION_DONE -> intent.getStringExtra(EXTRA_ID)?.let { done(it) }
        }
    }

    private suspend fun play(id: String, planned: LocalDateTime, last: Boolean) {
        val reminder = RemindersRepository(this).currentReminders().firstOrNull { it.id == id } ?: return
        // Done may have come in while an earlier reminder was playing.
        val played = RemindersRepository(this).history.first().firstOrNull { it.isFor(id, planned) } ?: return
        if (played.outcome == Outcome.Done || id in doneIds) return
        ReminderNotifications.showDue(this, played)
        val settings = SettingsRepository(this).current()
        val phrases = phrasesFor(settings.voiceLanguage, phoneLocale())
        val player = alarmPlayer ?: ReminderPlayer(this, AudioAttributes.USAGE_ALARM).also { alarmPlayer = it }
        // Done may have come in while the settings were read.
        if (id in doneIds) return
        playingId = id
        try {
            player.play(
                recording = if (reminder.hasRecording) ReminderFiles.recording(this, id) else null,
                sentence = phrases?.reminder(reminder.name),
                locale = phrases?.locale,
                rate = settings.speechRate,
                volume = settings.volumePercent / 100f,
                vibrate = settings.vibrate,
            )
        } finally {
            playingId = null
        }
        if (id in doneIds) return
        if (last) ReminderNotifications.showMissed(this, played.copy(outcome = Outcome.Missed))
    }

    private suspend fun done(id: String) {
        Alarms.markDone(this, id)
        val settings = SettingsRepository(this).current()
        val phrases = phrasesFor(settings.voiceLanguage, phoneLocale()) ?: return
        val player = voicePlayer ?: ReminderPlayer(this, AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY).also { voicePlayer = it }
        player.say(phrases.done, phrases.locale, settings.speechRate)
    }

    private fun startInForeground() {
        ReminderNotifications.createChannels(this)
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            ReminderNotifications.playing(this),
            // Before Android 14, 0 means "the type in the manifest".
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        alarmPlayer?.release()
        alarmPlayer = null
        voicePlayer?.release()
        voicePlayer = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        super.onDestroy()
    }

    companion object {
        private const val ACTION_PLAY = "io.github.nikoapps.reminders.action.PLAY"
        private const val ACTION_DONE = "io.github.nikoapps.reminders.action.DONE"
        private const val ACTION_STOP_SOUND = "io.github.nikoapps.reminders.action.STOP_SOUND"
        private const val EXTRA_IDS = "ids"
        private const val EXTRA_PLANNED = "planned"
        private const val EXTRA_LAST = "last"
        private const val EXTRA_ID = "id"
        private const val NOTIFICATION_ID = 1
        // Seven reminders at the same time, each up to 30 seconds, with room to spare.
        private const val WAKE_LOCK_TIMEOUT_MS = 5 * 60_000L

        /** Plays [due] one after the other. Throws if Android doesn't allow starting it right now. */
        fun play(context: Context, due: List<Played>) {
            val intent = Intent(context, ReminderService::class.java)
                .setAction(ACTION_PLAY)
                .putExtra(EXTRA_IDS, due.map { it.reminderId }.toTypedArray())
                .putExtra(EXTRA_PLANNED, due.map { it.planned.toString() }.toTypedArray())
                .putExtra(EXTRA_LAST, due.map { it.outcome == Outcome.Missed }.toBooleanArray())
            ContextCompat.startForegroundService(context, intent)
        }

        /** Done for reminder [id]. From the open app or the reminder screen. */
        fun done(context: Context, id: String) {
            ContextCompat.startForegroundService(context, doneServiceIntent(context, id))
        }

        /** True while the service is running, so stopping the sound doesn't start it for nothing. */
        @Volatile
        private var running = false

        /** Stops the reminder playing now, such as when Play again is pressed. Repeats carry on. */
        fun stopSound(context: Context) {
            if (!running) return
            ContextCompat.startForegroundService(context, Intent(context, ReminderService::class.java).setAction(ACTION_STOP_SOUND))
        }

        /** Done from a notification button. */
        fun doneIntent(context: Context, id: String): PendingIntent =
            PendingIntent.getForegroundService(
                context,
                id.hashCode(),
                doneServiceIntent(context, id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        private fun doneServiceIntent(context: Context, id: String): Intent =
            Intent(context, ReminderService::class.java).setAction(ACTION_DONE).putExtra(EXTRA_ID, id)

        /** The voice language follows the phone's own language, not the app's screen language. */
        private fun phoneLocale() = Resources.getSystem().configuration.locales[0]
    }
}
