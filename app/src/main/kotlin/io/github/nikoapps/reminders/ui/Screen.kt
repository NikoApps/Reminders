package io.github.nikoapps.reminders.ui

/**
 * The screens. Settings is reached through Unlock once a PIN is set; the PIN screen and the
 * reminder editor open from settings or setup and go back to where they came from. The reminder
 * screen opens from a tile on the grid.
 */
enum class Screen {
    Main,
    Reminder,
    Unlock,
    Settings,
    SetPin,
    Setup,
    EditReminder,
    ;

    /** Only a helper who knows the PIN gets here, once one is set. */
    val behindPin: Boolean
        get() = this != Main && this != Unlock && this != Reminder
}

/**
 * The screen to show when Android brings Reminders back after stopping it in the background. The
 * PIN was entered in the old process, so a screen behind the PIN goes back to Unlock while a PIN
 * is set. A config change, such as turning the phone, keeps the process and never comes here.
 */
fun screenAfterRestart(saved: Screen, pinSet: Boolean): Screen =
    if (pinSet && saved.behindPin) Screen.Unlock else saved

/**
 * The screen to keep when Reminders leaves the front, such as when Home is pressed. With a PIN
 * set, a screen behind it goes back to the grid, so the next person to open Reminders can't
 * change settings. Turning the phone ([changingConfig]) keeps it, and so does a screen Reminders
 * opened itself ([openedAnotherScreen]), such as the camera, so a helper is never thrown out
 * halfway through adding a reminder.
 */
fun screenAfterLeaving(current: Screen, pinSet: Boolean, changingConfig: Boolean, openedAnotherScreen: Boolean): Screen =
    if (pinSet && current.behindPin && !changingConfig && !openedAnotherScreen) Screen.Main else current
