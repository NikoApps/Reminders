package io.github.nikoapps.reminders.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.nikoapps.commons.health.HealthCheck
import io.github.nikoapps.commons.ui.HealthRows
import io.github.nikoapps.commons.ui.PhoneMakerRow
import io.github.nikoapps.commons.ui.PinRows
import io.github.nikoapps.commons.ui.SetupFrame
import io.github.nikoapps.commons.ui.SetupStep
import io.github.nikoapps.reminders.R
import io.github.nikoapps.reminders.reminders.Reminder
import io.github.nikoapps.reminders.settings.Settings
import io.github.nikoapps.commons.R as CommonsR

/**
 * First-run setup for the helper: reminders, PIN, then the phone settings reminders need, in the
 * spec's order. The frame with Next, Skip and Back is shared with other Niko apps; these are
 * Reminders' steps, using the same rows as settings. [step] is kept by the caller so it survives
 * a trip to the PIN screen or the reminder editor. [problems] are checked even before the first
 * reminder is added, so the helper can set the phone up in one visit.
 */
@Composable
fun SetupScreen(
    step: Int,
    onStep: (Int) -> Unit,
    settings: Settings,
    reminders: List<Reminder>,
    problems: List<HealthCheck>,
    onUpdate: ((Settings) -> Settings) -> Unit,
    onEditPlace: (Int) -> Unit,
    onSetPin: () -> Unit,
    onRemovePin: () -> Unit,
    onFix: (HealthCheck) -> Unit,
    onFinish: () -> Unit,
) {
    val steps = listOf(
        SetupStep(stringResource(R.string.section_reminders)) {
            StepText(stringResource(R.string.setup_reminders_info))
            RemindersOnScreenRow(settings, reminders, onUpdate)
            ReminderRows(reminders, settings.remindersOnScreen, onEditPlace)
        },
        SetupStep(stringResource(CommonsR.string.section_pin)) {
            PinRows(settings.helperPinHash != null, stringResource(R.string.info_pin), onSetPin, onRemovePin)
        },
        SetupStep(stringResource(R.string.section_phone)) {
            StepText(stringResource(R.string.setup_phone_info))
            if (problems.isEmpty()) StepText(stringResource(R.string.setup_phone_ok)) else HealthRows(problems, onFix)
            PhoneMakerRow(stringResource(R.string.info_phone_maker))
        },
    )
    // The saved step is only a number. If an update changed the steps, it may point past the end,
    // so it is kept in range here, before Back counts down from it.
    SetupFrame(steps = steps, step = step.coerceIn(0, steps.lastIndex), onStep = onStep, onFinish = onFinish)
}

@Composable
private fun StepText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
    )
}
