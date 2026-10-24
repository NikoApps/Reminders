package io.github.nikoapps.reminders.settings

import io.github.nikoapps.commons.settings.HelperPin

/**
 * Niko Reminders' helper PIN. The salt must never change once Reminders is released: PINs
 * already saved on phones would stop matching, and the helper would be locked out of settings.
 */
val RemindersHelperPin = HelperPin(salt = "niko-reminders-helper-pin:")
