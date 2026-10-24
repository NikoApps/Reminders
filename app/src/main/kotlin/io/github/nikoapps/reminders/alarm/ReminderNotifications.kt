package io.github.nikoapps.reminders.alarm

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import io.github.nikoapps.reminders.MainActivity
import io.github.nikoapps.reminders.R
import io.github.nikoapps.reminders.ReminderActivity
import io.github.nikoapps.reminders.reminders.Outcome
import io.github.nikoapps.reminders.reminders.Played
import io.github.nikoapps.reminders.reminders.lastPlay
import io.github.nikoapps.reminders.settings.KeepReminding
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The notifications a reminder brings. While it plays, one that opens the reminder screen over
 * the lock screen (its full-screen intent) and has a Done button. When it's missed, a quiet one
 * that says so until the next day or until Done. Each reminder has one notification at a time,
 * tagged with its id.
 *
 * When the phone is unlocked and in use, Android shows the reminder as a banner instead of the
 * full screen; the sound plays anyway and a tap opens the reminder screen.
 */
object ReminderNotifications {
    private const val CHANNEL_REMINDERS = "reminders"
    private const val CHANNEL_MISSED = "missed"
    private const val CHANNEL_PLAYING = "playing"
    private const val ID = 1

    fun createChannels(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            // Reminders play their own sound on the alarm stream, so the channel is silent.
            NotificationChannelCompat.Builder(CHANNEL_REMINDERS, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(context.getString(R.string.channel_reminders))
                .setDescription(context.getString(R.string.channel_reminders_description))
                .setSound(null, null)
                .setVibrationEnabled(false)
                .build(),
        )
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_MISSED, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.channel_missed))
                .setDescription(context.getString(R.string.channel_missed_description))
                .setSound(null, null)
                .setVibrationEnabled(false)
                .build(),
        )
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_PLAYING, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.channel_playing))
                .setDescription(context.getString(R.string.channel_playing_description))
                .setShowBadge(false)
                .build(),
        )
    }

    /** True when reminders can show: notifications allowed, and the reminders channel not turned off. */
    fun canShow(context: Context): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        val channel = manager.getNotificationChannelCompat(CHANNEL_REMINDERS) ?: return true
        return channel.importance != NotificationManagerCompat.IMPORTANCE_NONE
    }

    /** False on Android 14 and newer when full-screen reminders were turned off for Reminders. */
    fun canUseFullScreen(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            context.getSystemService<NotificationManager>()?.canUseFullScreenIntent() != false

    /**
     * The reminder is playing. Posted again at each play, so the reminder screen comes back over
     * the lock screen even after Back or Home hid it.
     */
    fun showDue(context: Context, played: Played) {
        val open = screenIntent(context, played.reminderId)
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(played.name)
            .setContentText(context.getString(R.string.notification_due, time(context, played)))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .addAction(0, context.getString(R.string.done), ReminderService.doneIntent(context, played.reminderId))
            .build()
        val manager = NotificationManagerCompat.from(context)
        manager.cancel(played.reminderId, ID)
        notify(context, played.reminderId, notification)
    }

    /** The reminder played for the whole keep reminding time, or the phone was off, without Done. */
    fun showMissed(context: Context, played: Played) {
        val notification = NotificationCompat.Builder(context, CHANNEL_MISSED)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_missed, played.name))
            .setContentText(context.getString(R.string.notification_missed_text, time(context, played)))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSilent(true)
            .setContentIntent(mainIntent(context))
            .addAction(0, context.getString(R.string.done), ReminderService.doneIntent(context, played.reminderId))
            .build()
        notify(context, played.reminderId, notification)
    }

    fun cancel(context: Context, reminderId: String) {
        NotificationManagerCompat.from(context).cancel(reminderId, ID)
    }

    /**
     * Takes away notifications that no longer apply: reminders done or removed, and missed ones
     * whose last play was before today. A reminder playing or missed today keeps its own.
     */
    fun sync(context: Context, history: List<Played>, today: LocalDate, keep: KeepReminding) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        val keep = history
            .groupBy { it.reminderId }
            .mapValues { (_, times) -> times.maxBy { it.planned } }
            .filterValues { it.outcome == Outcome.Due || (it.outcome == Outcome.Missed && lastPlay(it.planned, keep).toLocalDate() >= today) }
            .keys
        manager.activeNotifications
            .filter { it.id == ID && it.tag != null && it.tag !in keep }
            .forEach { manager.cancel(it.tag, ID) }
    }

    /** The quiet notification a foreground service must show while a reminder plays. */
    fun playing(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_PLAYING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_playing))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setOngoing(true)
            .build()

    private fun notify(context: Context, tag: String, notification: Notification) {
        createChannels(context)
        // Without the notification permission this does nothing; the health check says so.
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        try {
            NotificationManagerCompat.from(context).notify(tag, ID, notification)
        } catch (_: SecurityException) {
        }
    }

    private fun time(context: Context, played: Played): String =
        played.planned.toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(context.resources.configuration.locales[0]))

    private fun screenIntent(context: Context, reminderId: String): PendingIntent =
        PendingIntent.getActivity(
            context,
            reminderId.hashCode(),
            ReminderActivity.intent(context, reminderId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun mainIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
