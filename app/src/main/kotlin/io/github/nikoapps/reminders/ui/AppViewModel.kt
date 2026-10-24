package io.github.nikoapps.reminders.ui

import android.app.Application
import android.content.res.Resources
import android.media.AudioAttributes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.nikoapps.commons.speech.VoiceStatus
import io.github.nikoapps.reminders.alarm.Alarms
import io.github.nikoapps.reminders.alarm.ReminderNotifications
import io.github.nikoapps.reminders.alarm.ReminderPlayer
import io.github.nikoapps.reminders.alarm.ReminderService
import io.github.nikoapps.reminders.reminders.Played
import io.github.nikoapps.reminders.reminders.Reminder
import io.github.nikoapps.reminders.reminders.ReminderFiles
import io.github.nikoapps.reminders.reminders.RemindersRepository
import io.github.nikoapps.reminders.reminders.isOffScreen
import io.github.nikoapps.reminders.reminders.removeReminder as withoutReminder
import io.github.nikoapps.reminders.reminders.saveReminder as withReminderSaved
import io.github.nikoapps.reminders.settings.Settings
import io.github.nikoapps.reminders.settings.SettingsRepository
import io.github.nikoapps.reminders.speech.phrasesFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalTime
import java.util.UUID

/** [engine] is the speech engine's name, shown to helpers so they know which voice is in use. */
data class VoiceState(val voice: VoiceStatus = VoiceStatus.Starting, val engine: String? = null)

/** What to do with a reminder's photo or recording when it's saved. */
sealed interface FileEdit {
    data object Keep : FileEdit
    data object Remove : FileEdit

    /** A new file waiting in the cache, moved into place on save. */
    data class Replace(val path: String) : FileEdit
}

/** A reminder as the editor saves it. [id] is null for a new one. */
data class ReminderEdit(
    val id: String?,
    val name: String,
    val time: LocalTime,
    val repeat: io.github.nikoapps.reminders.reminders.Repeat,
    val place: Int,
    val photo: FileEdit,
    val recording: FileEdit,
    /** Shown when there's no photo. A new photo replaces it. */
    val picture: io.github.nikoapps.reminders.reminders.Picture?,
)

/** The reminders, their history and the helper's settings, shared by every screen in the app. */
class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepository = SettingsRepository(application)
    private val remindersRepository = RemindersRepository(application)

    /** Held while a reminder's files change, so a save and a removal never overlap. */
    private val files = Mutex()

    /** Plays a reminder when its tile is tapped, on the accessibility stream: someone asked to hear it now. */
    private val player = ReminderPlayer(application, AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
    private var hearing: Job? = null

    /**
     * False in a new process, until the screen saved before Android stopped Reminders has been
     * checked against the PIN. A config change keeps this view model, so it stays true then.
     */
    var screenRestored = false

    /** Null until the stored settings have loaded, so the screen never flashes defaults. */
    val settings: StateFlow<Settings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Null until loaded, so the grid never flashes empty. */
    val reminders: StateFlow<List<Reminder>?> = remindersRepository.reminders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val history: StateFlow<List<Played>> = remindersRepository.history
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** True while there are no reminders because the saved ones couldn't be read. */
    val unreadable: StateFlow<Boolean> = remindersRepository.unreadable
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _voice = MutableStateFlow(VoiceState())
    val voice: StateFlow<VoiceState> = _voice.asStateFlow()

    /** Runs each time the app comes to the front: anything due plays, alarms are put back, the voice is checked. */
    fun onResume() {
        ReminderNotifications.createChannels(getApplication())
        refreshAlarms()
        checkVoice()
    }

    fun refreshAlarms() {
        viewModelScope.launch { Alarms.refresh(getApplication()) }
    }

    fun update(transform: (Settings) -> Settings) {
        viewModelScope.launch {
            val before = settingsRepository.current().remindersOnScreen
            settingsRepository.update(transform)
            val after = settingsRepository.current().remindersOnScreen
            // Reminders back on a bigger grid start again from now, so the times they were off
            // the screen don't count as missed.
            if (after > before) {
                val now = System.currentTimeMillis()
                remindersRepository.updateReminders { all ->
                    all.map { if (isOffScreen(it.place, before) && !isOffScreen(it.place, after)) it.copy(activeFrom = now) else it }
                }
            }
            Alarms.refresh(getApplication())
            checkVoice()
        }
    }

    /** Whether the phone's voice can say reminders that have no recording, in the voice language. */
    fun checkVoice() {
        viewModelScope.launch {
            val settings = settingsRepository.current()
            val phrases = phrasesFor(settings.voiceLanguage, phoneLocale())
            val status = player.voiceStatus(phrases?.locale)
            _voice.value = VoiceState(status, player.engineName())
        }
    }

    /** Plays the reminder's message now: its recording, or its name in the phone's voice. */
    fun hear(reminder: Reminder) {
        hearing?.cancel()
        // The reminder may be playing on its own right now; one voice at a time.
        runCatching { ReminderService.stopSound(getApplication()) }
        hearing = viewModelScope.launch {
            player.stop()
            val settings = settingsRepository.current()
            val phrases = phrasesFor(settings.voiceLanguage, phoneLocale())
            val recording = if (reminder.hasRecording) withContext(Dispatchers.IO) { ReminderFiles.recording(getApplication(), reminder.id) } else null
            player.play(recording, phrases?.reminder(reminder.name), phrases?.locale, settings.speechRate, volume = 1f, vibrate = false)
        }
    }

    fun stopHearing() {
        hearing?.cancel()
        player.stop()
    }

    /** Says a pretend reminder in the phone's voice, so a helper hears how it sounds. */
    fun testVoice() {
        hearing?.cancel()
        hearing = viewModelScope.launch {
            val settings = settingsRepository.current()
            val phrases = phrasesFor(settings.voiceLanguage, phoneLocale()) ?: return@launch
            player.stop()
            player.say(phrases.reminder(phrases.testName), phrases.locale, settings.speechRate)
            checkVoice()
        }
    }

    /** Done for today: stops the reminder playing, says "Done", and the tile shows the tick. */
    fun markDone(reminder: Reminder) {
        stopHearing()
        ReminderService.done(getApplication(), reminder.id)
    }

    /** Saves the reminder: moves a new photo and recording into place, then puts the alarm in line with it. */
    fun saveReminder(edit: ReminderEdit) {
        viewModelScope.launch {
            files.withLock {
                val app = getApplication<Application>()
                val existing = edit.id?.let { id -> remindersRepository.currentReminders().firstOrNull { it.id == id } }
                val id = existing?.id ?: UUID.randomUUID().toString()
                val (photoVersion, recordingVersion) = withContext(Dispatchers.IO) {
                    val photo = applyFile(edit.photo, existing?.photoVersion ?: 0, { ReminderFiles.savePhoto(app, id, it) }, { ReminderFiles.deletePhoto(app, id) })
                    val recording = applyFile(
                        edit.recording,
                        existing?.recordingVersion ?: 0,
                        { ReminderFiles.saveRecording(app, id, it) },
                        { ReminderFiles.deleteRecording(app, id) },
                    )
                    remindersRepository.forgetUnreadable()
                    photo to recording
                }
                // A new time or new days start from now, so a time already past today isn't missed.
                val changed = existing == null || existing.time != edit.time || existing.repeat != edit.repeat
                val reminder = Reminder(
                    id = id,
                    name = edit.name,
                    time = edit.time,
                    repeat = edit.repeat,
                    place = edit.place,
                    photoVersion = photoVersion,
                    recordingVersion = recordingVersion,
                    activeFrom = if (changed) System.currentTimeMillis() else existing.activeFrom,
                    picture = if (photoVersion > 0) null else edit.picture,
                )
                remindersRepository.updateReminders { withReminderSaved(it, reminder) }
                if (edit.photo !is FileEdit.Keep) PhotoCache.forget(id)
                Alarms.refresh(app)
            }
            checkVoice()
        }
    }

    fun removeReminder(reminder: Reminder) {
        viewModelScope.launch {
            files.withLock {
                val app = getApplication<Application>()
                remindersRepository.updateReminders { withoutReminder(it, reminder.id) }
                withContext(Dispatchers.IO) {
                    ReminderFiles.deletePhoto(app, reminder.id)
                    ReminderFiles.deleteRecording(app, reminder.id)
                }
                PhotoCache.forget(reminder.id)
                ReminderNotifications.cancel(app, reminder.id)
                Alarms.refresh(app)
            }
        }
    }

    /**
     * The file's new version: up by one when replaced, 0 when removed, as it was otherwise. A new
     * file that couldn't be moved into place keeps the old one. The cache copy goes either way.
     */
    private fun applyFile(edit: FileEdit, version: Int, save: (File) -> Boolean, delete: () -> Unit): Int = when (edit) {
        FileEdit.Keep -> version
        FileEdit.Remove -> {
            delete()
            0
        }
        is FileEdit.Replace -> {
            val file = File(edit.path)
            val saved = save(file)
            file.delete()
            if (saved) version + 1 else version
        }
    }

    override fun onCleared() {
        player.release()
    }

    /** The voice language follows the phone's own language, not the app's screen language. */
    private fun phoneLocale() = Resources.getSystem().configuration.locales[0]
}
