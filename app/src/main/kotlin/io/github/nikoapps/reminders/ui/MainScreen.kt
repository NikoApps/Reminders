package io.github.nikoapps.reminders.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.nikoapps.commons.theme.NikoAccent
import io.github.nikoapps.commons.theme.NikoColors
import io.github.nikoapps.commons.theme.NikoTheme
import io.github.nikoapps.reminders.R
import io.github.nikoapps.reminders.reminders.Outcome
import io.github.nikoapps.reminders.reminders.Reminder
import io.github.nikoapps.reminders.reminders.Repeat
import io.github.nikoapps.reminders.reminders.placesOnScreen
import java.time.LocalTime

/** One place on the grid: the reminder there and what happened to it today, or null for an empty place. */
data class Tile(val reminder: Reminder, val today: Outcome?)

/**
 * The main screen: title bar, the fixed grid of reminders, and the hint line. It never scrolls.
 *
 * Every part has a height that depends only on the font size, never on what's saved or what the
 * [hint] says, so tiles stay exactly where they are. [tiles] has one entry per place.
 */
@Composable
fun MainScreen(
    tiles: List<Tile?>,
    columns: Int,
    hint: String,
    onOpen: (Reminder) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        TitleBar(onOpenSettings = onOpenSettings)
        ReminderGrid(
            tiles = tiles,
            columns = columns,
            onOpen = onOpen,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        )
        HintLine(
            text = hint,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        )
    }
}

/**
 * App name centred, settings at the top right. The settings button takes at most a third of the
 * width and the name gets the middle, so the name stays centred. Both shrink their text rather
 * than wrap at large font sizes.
 */
@Composable
private fun TitleBar(onOpenSettings: () -> Unit) {
    Layout(
        content = {
            BasicText(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge.copy(
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
                // Small enough that "Niko Reminders" still fits on one line at 200% font size.
                autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = MaterialTheme.typography.titleLarge.fontSize),
                modifier = Modifier.semantics { heading() },
            )
            SettingsButton(onClick = onOpenSettings)
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val button = measurables[1].measure(Constraints(maxWidth = width / 3, maxHeight = constraints.maxHeight))
        val titleWidth = (width - 2 * button.width).coerceAtLeast(0)
        val title = measurables[0].measure(Constraints.fixedWidth(titleWidth))
        val height = maxOf(button.height, title.height)
        layout(width, height) {
            title.place(button.width, (height - title.height) / 2)
            button.place(width - button.width, (height - button.height) / 2)
        }
    }
}

/** Gear with "Settings" under it. One touch target, at least 64dp, read once by TalkBack. */
@Composable
private fun SettingsButton(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .sizeIn(minWidth = 64.dp, minHeight = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_settings),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(28.dp),
        )
        BasicText(
            text = stringResource(R.string.settings_open),
            style = SettingsLabelStyle.copy(color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = SettingsLabelStyle.fontSize),
        )
    }
}

private val SettingsLabelStyle = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold)

/** The grid's shape comes only from the number of places, never from how many reminders are saved. */
@Composable
private fun ReminderGrid(tiles: List<Tile?>, columns: Int, onOpen: (Reminder) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        tiles.chunked(columns).forEach { row ->
            Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { tile ->
                    val cell = Modifier.weight(1f).fillMaxHeight()
                    // An empty place is plain space: not focusable, skipped by TalkBack.
                    if (tile == null) Spacer(cell) else ReminderTile(tile = tile, onClick = { onOpen(tile.reminder) }, modifier = cell)
                }
            }
        }
    }
}

/**
 * The whole tile is one button: the photo, the name, then the time and today's Done or Missed.
 * TalkBack says "White pill, 8:00 AM, done".
 */
@Composable
private fun ReminderTile(tile: Tile, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val reminder = tile.reminder
    val time = timeText(reminder.time)
    val state = when (tile.today) {
        Outcome.Done -> stringResource(R.string.today_done)
        Outcome.Missed -> stringResource(R.string.today_missed)
        Outcome.Due, null -> null
    }
    val label = if (state == null) {
        stringResource(R.string.tile_label, reminder.name, time)
    } else {
        stringResource(R.string.tile_label_today, reminder.name, time, state)
    }
    val shape = RoundedCornerShape(24.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = modifier
            .sizeIn(minWidth = 96.dp, minHeight = 96.dp)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Button
                onClick { onClick(); true }
            },
    ) {
        BoxWithConstraints(modifier = Modifier.padding(10.dp)) {
            // The name and the time line get up to 25% of the tile each and shrink to fit; the photo takes the rest.
            val lineMaxHeight = maxHeight * 0.25f
            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ReminderPicture(
                        reminder = reminder,
                        modifier = Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true),
                    )
                }
                TileLine(
                    text = reminder.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 6.dp).heightIn(max = lineMaxHeight),
                )
                Row(
                    modifier = Modifier.heightIn(max = lineMaxHeight),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    TileLine(
                        text = time,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    when (tile.today) {
                        Outcome.Done -> TodayMark(
                            text = state.orEmpty(),
                            color = successColor(),
                            tick = true,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Outcome.Missed -> TodayMark(
                            text = state.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            tick = false,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Outcome.Due, null -> Unit
                    }
                }
            }
        }
    }
}

/** "Done" with a tick, or "Missed", after the time. */
@Composable
private fun TodayMark(text: String, color: Color, tick: Boolean, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (tick) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp),
            )
        }
        TileLine(text = text, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = color)
    }
}

/** Niko's success green for the theme in use. Material's scheme has no role for it. Meets AAA on the surface in light and dark. */
@Composable
fun successColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) NikoColors.Dark.Success else NikoColors.Light.Success

/** One line that shrinks to fit rather than wrapping, at any font size. */
@Composable
private fun TileLine(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    BasicText(
        text = text,
        style = shrinkingLines(style).copy(color = color, textAlign = TextAlign.Center),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = style.fontSize),
        modifier = modifier,
    )
}

/**
 * [style] with its line height as a share of the font size, so lines get shorter as auto-sized
 * text shrinks. A line height in sp stays the same, and at 200% font size it no longer fits the
 * space the text has, so it's cut off however small the letters get.
 */
fun shrinkingLines(style: TextStyle): TextStyle =
    if (style.lineHeight.isSp && style.fontSize.isSp) style.copy(lineHeight = (style.lineHeight.value / style.fontSize.value).em) else style

/**
 * How to use the screen, or what will stop a reminder. Always two lines tall (capped at 88dp),
 * whatever it says, so the grid above never moves. The text shrinks until two lines fit. TalkBack
 * reads it when it changes.
 */
@Composable
private fun HintLine(text: String, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.bodyMedium
    val height = with(LocalDensity.current) { (style.lineHeight * 2).toDp() }.coerceAtMost(88.dp)
    Box(modifier = modifier.height(height), contentAlignment = Alignment.Center) {
        BasicText(
            text = text,
            style = shrinkingLines(style).copy(color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center),
            maxLines = 2,
            autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = style.fontSize),
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

private fun previewTiles(names: List<String>): List<Tile?> {
    val reminders = names.mapIndexed { place, name ->
        Reminder(id = "r$place", name = name, time = LocalTime.of(8 + place * 2, 0), repeat = Repeat.EveryDay, place = place)
    }
    val outcomes = listOf(Outcome.Done, null, Outcome.Missed, null, null, Outcome.Done)
    return placesOnScreen(reminders, 6).mapIndexed { place, reminder -> reminder?.let { Tile(it, outcomes[place]) } }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun MainScreenPreview() {
    NikoTheme(accent = NikoAccent.Reminders) {
        Surface(color = MaterialTheme.colorScheme.background) {
            MainScreen(
                tiles = previewTiles(listOf("White pill", "Prayer", "Call Ram", "Lunch")),
                columns = 2,
                hint = stringResource(R.string.hint_ready),
                onOpen = {},
                onOpenSettings = {},
            )
        }
    }
}

/** Every place full, with long names and a two-line hint, at 200% font size on a small phone: nothing may be cut off. */
@Preview(showBackground = true, widthDp = 360, heightDp = 640, fontScale = 2f)
@Composable
private fun MainScreenFullLargeTextPreview() {
    NikoTheme(accent = NikoAccent.Reminders) {
        Surface(color = MaterialTheme.colorScheme.background) {
            MainScreen(
                tiles = previewTiles(listOf("White pill", "Blood pressure", "Prayer", "Call Ram", "Lunch", "Eye drops")),
                columns = 2,
                hint = stringResource(R.string.hint_full_screen),
                onOpen = {},
                onOpenSettings = {},
            )
        }
    }
}
