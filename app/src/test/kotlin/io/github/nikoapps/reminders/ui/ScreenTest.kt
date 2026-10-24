package io.github.nikoapps.reminders.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenTest {

    @Test
    fun `with a PIN, helper screens go back to the grid when Reminders leaves the front`() {
        assertEquals(Screen.Main, screenAfterLeaving(Screen.Settings, pinSet = true, changingConfig = false, openedAnotherScreen = false))
        assertEquals(Screen.Settings, screenAfterLeaving(Screen.Settings, pinSet = false, changingConfig = false, openedAnotherScreen = false))
        assertEquals(Screen.EditReminder, screenAfterLeaving(Screen.EditReminder, pinSet = true, changingConfig = false, openedAnotherScreen = true))
        assertEquals(Screen.Reminder, screenAfterLeaving(Screen.Reminder, pinSet = true, changingConfig = false, openedAnotherScreen = false))
    }

    @Test
    fun `after a restart, helper screens ask for the PIN again`() {
        assertEquals(Screen.Unlock, screenAfterRestart(Screen.EditReminder, pinSet = true))
        assertEquals(Screen.Main, screenAfterRestart(Screen.Main, pinSet = true))
    }
}
