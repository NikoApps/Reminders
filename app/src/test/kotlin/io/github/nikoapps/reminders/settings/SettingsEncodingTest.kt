package io.github.nikoapps.reminders.settings

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsEncodingTest {

    @Test
    fun `settings read back as they were`() {
        val settings = Settings(
            remindersOnScreen = 6,
            theme = ThemeChoice.Dark,
            volumePercent = 60,
            keepReminding = KeepReminding.For60,
            vibrate = false,
            voiceLanguage = "ne-NP",
            speechRate = 1.1f,
            setupDone = true,
            helperPinHash = "abc",
        )
        val prefs = mutablePreferencesOf().apply { write(settings) }
        assertEquals(settings, prefs.toSettings())
    }

    @Test
    fun `nothing stored means the spec's defaults`() {
        val d = mutablePreferencesOf().toSettings()
        assertEquals(4, d.remindersOnScreen)
        assertEquals(80, d.volumePercent)
        assertEquals(KeepReminding.For30, d.keepReminding)
        assertEquals(true, d.vibrate)
    }

    @Test
    fun `an unknown choice from a newer version falls back to its default`() {
        val prefs = mutablePreferencesOf(stringPreferencesKey("keep_reminding") to "For90")
        assertEquals(KeepReminding.For30, prefs.toSettings().keepReminding)
    }
}
