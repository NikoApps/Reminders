package io.github.nikoapps.reminders.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.nikoapps.commons.health.HealthCheck
import io.github.nikoapps.commons.speech.VoiceStatus
import io.github.nikoapps.commons.ui.voiceMessage
import io.github.nikoapps.reminders.R
import io.github.nikoapps.reminders.health.RemindersHealth

/** What the hint line under the grid says. Problems that stop a reminder come first. */
enum class Hint {
    Ready,
    NoReminders,

    /** Nothing is on the grid because the saved reminders couldn't be read. */
    Unreadable,

    /** Exact alarms aren't allowed, so reminders may come late. */
    ExactAlarms,

    /** A reminder without a recording has no voice to say it. */
    Voice,

    /** Notifications are off, so the reminder screen can't come up. */
    Notifications,

    /** Full-screen reminders are off, so a reminder plays but doesn't show over the lock screen. */
    FullScreen,

    /** The alarm volume is at zero. */
    Volume,

    /** A phone setting, such as battery use, may stop reminders later. */
    NeedsAttention,
}

/**
 * With no reminders there's nothing to warn about. Then exact alarms and the voice come first, as
 * the spec asks, then what stops a reminder showing or being heard, then any other problem listed
 * in settings. [voiceNeeded] is true while a reminder has no recording, so the voice would say it.
 */
fun hintFor(
    hasReminders: Boolean,
    unreadable: Boolean,
    problems: List<HealthCheck>,
    voice: VoiceStatus,
    voiceNeeded: Boolean,
): Hint = when {
    !hasReminders && unreadable -> Hint.Unreadable
    !hasReminders -> Hint.NoReminders
    RemindersHealth.ExactAlarms in problems -> Hint.ExactAlarms
    voiceNeeded && voice.isProblem -> Hint.Voice
    RemindersHealth.Notifications in problems -> Hint.Notifications
    RemindersHealth.FullScreen in problems -> Hint.FullScreen
    RemindersHealth.AlarmVolume in problems -> Hint.Volume
    problems.isNotEmpty() -> Hint.NeedsAttention
    else -> Hint.Ready
}

/** Starting isn't a problem: the voice is on its way. */
val VoiceStatus.isProblem: Boolean
    get() = this != VoiceStatus.Starting && this != VoiceStatus.Ready

@Composable
fun hintText(hint: Hint, voice: VoiceStatus): String = when (hint) {
    Hint.Ready -> stringResource(R.string.hint_ready)
    Hint.NoReminders -> stringResource(R.string.hint_no_reminders)
    Hint.Unreadable -> stringResource(R.string.hint_unreadable)
    Hint.ExactAlarms -> stringResource(R.string.hint_exact_alarms)
    Hint.Voice -> voiceMessage(voice) ?: stringResource(R.string.hint_ready)
    Hint.Notifications -> stringResource(R.string.hint_notifications)
    Hint.FullScreen -> stringResource(R.string.hint_full_screen)
    Hint.Volume -> stringResource(R.string.hint_volume)
    Hint.NeedsAttention -> stringResource(R.string.hint_needs_attention)
}
