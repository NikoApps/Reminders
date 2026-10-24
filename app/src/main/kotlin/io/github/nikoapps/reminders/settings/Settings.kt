package io.github.nikoapps.reminders.settings

import io.github.nikoapps.commons.settings.SpeechSpeed

/**
 * How long a reminder keeps playing without Done: every [REPEAT_EVERY_MINUTES] minutes for
 * [minutes], then it's marked missed. [Off] plays it once.
 */
enum class KeepReminding(val minutes: Int) {
    Off(0),
    For15(15),
    For30(30),
    For60(60),
}

/** Minutes between plays while a reminder waits for Done. */
const val REPEAT_EVERY_MINUTES = 5

/** Light or dark screens. Follows the phone unless a helper picks one. */
enum class ThemeChoice { Phone, Light, Dark }

/** Everything a helper can set. Defaults follow the Reminders spec. */
data class Settings(
    /** How many places the grid has: 1, 2, 4 or 6. See [REMINDERS_ON_SCREEN_CHOICES]. */
    val remindersOnScreen: Int = DEFAULT_REMINDERS_ON_SCREEN,
    val theme: ThemeChoice = ThemeChoice.Phone,
    /** Reminder volume as a share of the alarm volume, in percent. */
    val volumePercent: Int = 80,
    val keepReminding: KeepReminding = KeepReminding.For30,
    val vibrate: Boolean = true,
    /** Voice language as a BCP 47 tag such as "en", or null to follow the phone's language. */
    val voiceLanguage: String? = null,
    /** 1.0 is the engine's normal speed. The default is slightly slower, as in Clock. */
    val speechRate: Float = SpeechSpeed.Normal.rate,
    /** The helper finished or skipped first-run setup. */
    val setupDone: Boolean = false,
    /** The helper's 4-digit PIN hashed with [RemindersHelperPin], or null when there is no PIN. */
    val helperPinHash: String? = null,
) {
    val speechSpeed: SpeechSpeed
        get() = SpeechSpeed.closestTo(speechRate)
}

/** The grid sizes a helper can pick. The layout depends only on this number. */
val REMINDERS_ON_SCREEN_CHOICES = listOf(1, 2, 4, 6)

const val DEFAULT_REMINDERS_ON_SCREEN = 4

/** The most places a grid can have, and so the most reminders Reminders keeps. */
const val MAX_PLACES = 6

/** The reminder volume choices, in percent of the alarm volume. */
val VOLUME_CHOICES = listOf(40, 60, 80, 100)
