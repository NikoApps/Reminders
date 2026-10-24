package io.github.nikoapps.reminders.ui

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/** The voice and tone rules that can be checked: no exclamation marks anywhere on screen. */
class StringsTest {

    @Test
    fun `no exclamation marks`() {
        val text = File("src/main/res/values/strings.xml").readText()
        assertFalse(Regex(">[^<]*![^<]*<").containsMatchIn(text))
    }
}
