package io.github.nikoapps.reminders.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nikoapps.commons.theme.NikoAccent
import io.github.nikoapps.commons.theme.NikoTheme
import io.github.nikoapps.commons.ui.ScreenTopBar
import io.github.nikoapps.commons.ui.SecondaryButton
import io.github.nikoapps.reminders.R
import io.github.nikoapps.reminders.reminders.Outcome
import io.github.nikoapps.reminders.reminders.Reminder
import io.github.nikoapps.reminders.reminders.Repeat
import java.time.LocalTime
import io.github.nikoapps.commons.R as CommonsR

/**
 * The reminder screen: a large photo, the name and time, Play again, and Done at the bottom.
 * Nothing else. It shows over the lock screen when a reminder comes due, and opens from a tile.
 *
 * [canDone] is true while the reminder is due or missed today, so a late dose can still be
 * marked; [today] Done shows a tick instead. [onBack] adds a back arrow, for the open app; over
 * the lock screen Back hides the screen and only Done stops the repeats.
 */
@Composable
fun ReminderScreen(
    reminder: Reminder,
    today: Outcome?,
    canDone: Boolean,
    onPlayAgain: () -> Unit,
    onDone: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val time = timeText(reminder.time)
    val announce = stringResource(R.string.reminder_screen_title, reminder.name)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .semantics { paneTitle = announce },
    ) {
        if (onBack != null) {
            ScreenTopBar(
                title = stringResource(R.string.reminder_screen_heading),
                backIcon = painterResource(CommonsR.drawable.ic_arrow_back),
                backLabel = stringResource(CommonsR.string.back),
                onBack = onBack,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                ReminderPicture(
                    reminder = reminder,
                    modifier = Modifier.aspectRatio(1f, matchHeightConstraintsFirst = true),
                )
            }
            BasicText(
                text = reminder.name,
                style = shrinkingLines(MaterialTheme.typography.headlineLarge).copy(
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = MaterialTheme.typography.headlineLarge.fontSize),
                modifier = Modifier.fillMaxWidth().heightIn(max = 120.dp).semantics { heading() },
            )
            BasicText(
                text = if (today == Outcome.Done) stringResource(R.string.reminder_time_done, time) else time,
                style = shrinkingLines(MaterialTheme.typography.titleLarge).copy(
                    color = if (today == Outcome.Done) successColor() else MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = MaterialTheme.typography.titleLarge.fontSize),
                modifier = Modifier.fillMaxWidth(),
            )
            SecondaryButton(text = stringResource(R.string.play_again), onClick = onPlayAgain)
            if (canDone) DoneButton(onClick = onDone)
        }
    }
}

/** Done with a tick: full width and at least 96dp tall, the biggest thing on the screen after the photo. */
@Composable
private fun DoneButton(onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painter = painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(12.dp))
            BasicText(
                text = stringResource(R.string.done),
                style = MaterialTheme.typography.headlineMedium.copy(color = MaterialTheme.colorScheme.onPrimary),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = MaterialTheme.typography.headlineMedium.fontSize),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640, fontScale = 2f)
@Composable
private fun ReminderScreenPreview() {
    NikoTheme(accent = NikoAccent.Reminders) {
        Surface(color = MaterialTheme.colorScheme.background) {
            ReminderScreen(
                reminder = Reminder(id = "a", name = "White pill", time = LocalTime.of(8, 0), repeat = Repeat.EveryDay, place = 0),
                today = Outcome.Due,
                canDone = true,
                onPlayAgain = {},
                onDone = {},
            )
        }
    }
}
