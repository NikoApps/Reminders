package io.github.nikoapps.reminders.ui

import android.icu.text.ListFormatter
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.nikoapps.commons.ui.ChoiceRow
import io.github.nikoapps.commons.ui.SwitchRow
import io.github.nikoapps.commons.ui.ValueRow
import io.github.nikoapps.reminders.R
import io.github.nikoapps.reminders.reminders.Outcome
import io.github.nikoapps.reminders.reminders.PlacePosition
import io.github.nikoapps.reminders.reminders.Played
import io.github.nikoapps.reminders.reminders.Reminder
import io.github.nikoapps.reminders.reminders.isOffScreen
import io.github.nikoapps.reminders.reminders.placePosition
import io.github.nikoapps.reminders.reminders.placesOnScreen
import io.github.nikoapps.reminders.reminders.remindersOffScreen
import io.github.nikoapps.reminders.settings.KeepReminding
import io.github.nikoapps.reminders.settings.REMINDERS_ON_SCREEN_CHOICES
import io.github.nikoapps.reminders.settings.Settings
import io.github.nikoapps.reminders.settings.ThemeChoice
import io.github.nikoapps.reminders.settings.VOLUME_CHOICES
import java.time.LocalDate
import java.time.format.TextStyle
import io.github.nikoapps.commons.R as CommonsR

// Reminders' rows shared by the settings screen and first-run setup, so both always look and
// behave the same. Rows every Niko app has, such as voice and PIN, are in Commons.

/**
 * One row per place on screen, in grid order: the reminder there with its time, or Empty. Then
 * any kept past the grid, as not on the screen now, so they can still be changed or removed.
 * Each opens the reminder editor for that place.
 */
@Composable
fun ReminderRows(reminders: List<Reminder>, remindersOnScreen: Int, onEditPlace: (Int) -> Unit) {
    val chevron = painterResource(CommonsR.drawable.ic_chevron_right)
    placesOnScreen(reminders, remindersOnScreen).forEachIndexed { place, reminder ->
        ValueRow(
            title = placeName(place, remindersOnScreen),
            value = reminder?.let { stringResource(R.string.reminder_row, it.name, timeText(it.time)) } ?: stringResource(R.string.place_empty),
            trailing = chevron,
            onClick = { onEditPlace(place) },
        )
    }
    remindersOffScreen(reminders, remindersOnScreen).forEach { reminder ->
        ValueRow(
            title = stringResource(R.string.place_off_screen),
            value = reminder.name,
            trailing = chevron,
            onClick = { onEditPlace(reminder.place) },
        )
    }
}

/** Where [place] is, as a helper sees it on the main screen, or not on the screen now. */
@Composable
fun placeName(place: Int, remindersOnScreen: Int): String =
    if (isOffScreen(place, remindersOnScreen)) {
        stringResource(R.string.place_off_screen)
    } else {
        stringResource(placeLabel(placePosition(place, remindersOnScreen)))
    }

/**
 * Each choice says which reminders it leaves off the screen, such as "2 reminders. Not on the
 * screen, and won't play: Prayer and Lunch", so lowering the number never stops one without saying so.
 */
@Composable
fun RemindersOnScreenRow(settings: Settings, reminders: List<Reminder>, onUpdate: ((Settings) -> Settings) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    ChoiceRow(
        title = stringResource(R.string.setting_on_screen),
        info = stringResource(R.string.info_on_screen),
        options = REMINDERS_ON_SCREEN_CHOICES,
        selected = settings.remindersOnScreen,
        optionLabel = { count ->
            val label = pluralStringResource(R.plurals.reminders_count, count, count)
            val hidden = remindersOffScreen(reminders, count)
            if (hidden.isEmpty()) {
                label
            } else {
                stringResource(R.string.reminders_count_hiding, label, ListFormatter.getInstance(locale).format(hidden.map { it.name }))
            }
        },
        onSelect = { count -> onUpdate { it.copy(remindersOnScreen = count) } },
        cancelLabel = stringResource(CommonsR.string.cancel),
    )
}

@Composable
fun ThemeRow(settings: Settings, onUpdate: ((Settings) -> Settings) -> Unit) {
    ChoiceRow(
        title = stringResource(R.string.setting_theme),
        options = ThemeChoice.entries,
        selected = settings.theme,
        optionLabel = { stringResource(it.label()) },
        onSelect = { theme -> onUpdate { it.copy(theme = theme) } },
        cancelLabel = stringResource(CommonsR.string.cancel),
    )
}

@Composable
fun VolumeRow(settings: Settings, onUpdate: ((Settings) -> Settings) -> Unit) {
    ChoiceRow(
        title = stringResource(R.string.setting_volume),
        info = stringResource(R.string.info_volume),
        options = VOLUME_CHOICES,
        selected = VOLUME_CHOICES.minBy { kotlin.math.abs(it - settings.volumePercent) },
        optionLabel = { stringResource(R.string.volume_percent, it) },
        onSelect = { volume -> onUpdate { it.copy(volumePercent = volume) } },
        cancelLabel = stringResource(CommonsR.string.cancel),
    )
}

@Composable
fun KeepRemindingRow(settings: Settings, onUpdate: ((Settings) -> Settings) -> Unit) {
    ChoiceRow(
        title = stringResource(R.string.setting_keep_reminding),
        info = stringResource(R.string.info_keep_reminding),
        options = KeepReminding.entries,
        selected = settings.keepReminding,
        optionLabel = { stringResource(it.label()) },
        onSelect = { keep -> onUpdate { it.copy(keepReminding = keep) } },
        cancelLabel = stringResource(CommonsR.string.cancel),
    )
}

@Composable
fun VibrateRow(settings: Settings, onUpdate: ((Settings) -> Settings) -> Unit) {
    SwitchRow(
        title = stringResource(R.string.setting_vibrate),
        checked = settings.vibrate,
        onCheckedChange = { on -> onUpdate { it.copy(vibrate = on) } },
        info = stringResource(R.string.info_vibrate),
    )
}

/**
 * The last 7 days, newest first: "Today, 8:00 AM, White pill, done at 8:04". A family member can
 * check on a visit that reminders play and get done.
 */
@Composable
fun HistoryRows(history: List<Played>, today: LocalDate) {
    if (history.isEmpty()) {
        HistoryLine(stringResource(R.string.history_nothing))
        return
    }
    history.sortedByDescending { it.planned }.forEach { played ->
        val day = when (played.planned.toLocalDate()) {
            today -> stringResource(R.string.history_today)
            today.minusDays(1) -> stringResource(R.string.history_yesterday)
            else -> played.planned.dayOfWeek.getDisplayName(TextStyle.FULL, LocalConfiguration.current.locales[0])
        }
        val time = timeText(played.planned.toLocalTime())
        val outcome = when (played.outcome) {
            Outcome.Done -> played.doneAt?.let { stringResource(R.string.history_done_at, timeText(it.toLocalTime())) } ?: stringResource(R.string.today_done)
            Outcome.Missed -> stringResource(R.string.history_missed)
            Outcome.Due -> stringResource(R.string.history_playing)
        }
        HistoryLine(stringResource(R.string.history_line, day, time, played.name, outcome))
    }
}

@Composable
private fun HistoryLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
    )
}

@StringRes
fun placeLabel(position: PlacePosition): Int = when (position) {
    PlacePosition.Only -> R.string.place_only
    PlacePosition.Top -> R.string.place_top
    PlacePosition.Bottom -> R.string.place_bottom
    PlacePosition.TopLeft -> R.string.place_top_left
    PlacePosition.TopRight -> R.string.place_top_right
    PlacePosition.MiddleLeft -> R.string.place_middle_left
    PlacePosition.MiddleRight -> R.string.place_middle_right
    PlacePosition.BottomLeft -> R.string.place_bottom_left
    PlacePosition.BottomRight -> R.string.place_bottom_right
}

@StringRes
fun ThemeChoice.label(): Int = when (this) {
    ThemeChoice.Phone -> R.string.theme_phone
    ThemeChoice.Light -> R.string.theme_light
    ThemeChoice.Dark -> R.string.theme_dark
}

@StringRes
fun KeepReminding.label(): Int = when (this) {
    KeepReminding.Off -> R.string.keep_off
    KeepReminding.For15 -> R.string.keep_15
    KeepReminding.For30 -> R.string.keep_30
    KeepReminding.For60 -> R.string.keep_60
}
