package io.github.nikoapps.reminders.settings

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Reads and writes [Settings]. One DataStore per process, shared by the app, the alarm and the reminder service. */
class SettingsRepository(context: Context) {
    private val store = context.deviceDataStore("settings")

    val settings: Flow<Settings> = store.data.map { it.toSettings() }

    suspend fun current(): Settings = settings.first()

    suspend fun update(transform: (Settings) -> Settings) {
        store.edit { prefs ->
            val updated = transform(prefs.toSettings())
            prefs.write(updated)
        }
    }
}

private object Keys {
    val remindersOnScreen = intPreferencesKey("reminders_on_screen")
    val theme = stringPreferencesKey("theme")
    val volume = intPreferencesKey("volume_percent")
    val keepReminding = stringPreferencesKey("keep_reminding")
    val vibrate = booleanPreferencesKey("vibrate")
    val voiceLanguage = stringPreferencesKey("voice_language")
    val speechRate = floatPreferencesKey("speech_rate")
    val setupDone = booleanPreferencesKey("setup_done")
    val pinHash = stringPreferencesKey("helper_pin_hash")
}

/** Reads stored settings. Anything missing or unknown, such as an option from a newer version, falls back to its default. */
internal fun Preferences.toSettings(): Settings {
    val d = Settings()
    return Settings(
        remindersOnScreen = this[Keys.remindersOnScreen]?.takeIf { it in REMINDERS_ON_SCREEN_CHOICES } ?: d.remindersOnScreen,
        theme = this[Keys.theme]?.let { name -> ThemeChoice.entries.firstOrNull { it.name == name } } ?: d.theme,
        volumePercent = this[Keys.volume]?.takeIf { it in 1..100 } ?: d.volumePercent,
        keepReminding = this[Keys.keepReminding]?.let { name -> KeepReminding.entries.firstOrNull { it.name == name } } ?: d.keepReminding,
        vibrate = this[Keys.vibrate] ?: d.vibrate,
        voiceLanguage = this[Keys.voiceLanguage],
        speechRate = this[Keys.speechRate]?.takeIf { it > 0f } ?: d.speechRate,
        setupDone = this[Keys.setupDone] ?: d.setupDone,
        helperPinHash = this[Keys.pinHash],
    )
}

internal fun MutablePreferences.write(s: Settings) {
    this[Keys.remindersOnScreen] = s.remindersOnScreen
    this[Keys.theme] = s.theme.name
    this[Keys.volume] = s.volumePercent
    this[Keys.keepReminding] = s.keepReminding.name
    this[Keys.vibrate] = s.vibrate
    if (s.voiceLanguage == null) remove(Keys.voiceLanguage) else this[Keys.voiceLanguage] = s.voiceLanguage
    this[Keys.speechRate] = s.speechRate
    this[Keys.setupDone] = s.setupDone
    if (s.helperPinHash == null) remove(Keys.pinHash) else this[Keys.pinHash] = s.helperPinHash
}
