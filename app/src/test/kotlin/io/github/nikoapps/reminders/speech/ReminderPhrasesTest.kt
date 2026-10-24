package io.github.nikoapps.reminders.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class ReminderPhrasesTest {

    @Test
    fun `every voice language has its sentences`() {
        VOICE_LANGUAGES.filterNotNull().forEach { assertNotNull(it, phrasesFor(it, Locale.ENGLISH)) }
    }

    @Test
    fun `the phone's language is used when none is chosen`() {
        assertEquals("ne", phrasesFor(null, Locale.forLanguageTag("ne-NP"))?.locale?.language)
        assertNull(phrasesFor(null, Locale.JAPANESE))
    }

    @Test
    fun `old Indonesian code works`() {
        assertEquals("id", phrasesFor(null, Locale.forLanguageTag("in"))?.locale?.language)
    }

    @Test
    fun `the name goes in as typed, without a doubled full stop`() {
        val english = phrasesFor("en", Locale.ENGLISH)!!
        assertEquals("It's time: White pill.", english.reminder("White pill"))
        assertEquals("It's time: White pill.", english.reminder(" White pill. "))
    }

    @Test
    fun `no digits are ever said`() {
        VOICE_LANGUAGES.filterNotNull().forEach { tag ->
            val phrases = phrasesFor(tag, Locale.ENGLISH)!!
            listOf(phrases.reminder(phrases.testName), phrases.done).forEach { assertFalse(tag, it.any(Char::isDigit)) }
        }
    }
}
