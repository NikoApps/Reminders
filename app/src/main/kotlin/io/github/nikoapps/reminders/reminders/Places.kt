package io.github.nikoapps.reminders.reminders

import io.github.nikoapps.reminders.settings.DEFAULT_REMINDERS_ON_SCREEN
import io.github.nikoapps.reminders.settings.MAX_PLACES
import io.github.nikoapps.reminders.settings.REMINDERS_ON_SCREEN_CHOICES

// Where reminders sit on the grid. Pure functions, so the "tiles never move" rule is tested on
// the JVM: nothing here ever changes the place of any reminder but the one asked about. The same
// rules as Niko Dialer's faces.

/** The grid's shape. Places fill row by row. */
data class GridShape(val columns: Int, val rows: Int) {
    val places: Int get() = columns * rows
}

/** 1: one tile, 2: two stacked, 4: two by two, 6: two across and three down. */
fun gridShape(remindersOnScreen: Int): GridShape =
    when (remindersOnScreen.takeIf { it in REMINDERS_ON_SCREEN_CHOICES } ?: DEFAULT_REMINDERS_ON_SCREEN) {
        1 -> GridShape(columns = 1, rows = 1)
        2 -> GridShape(columns = 1, rows = 2)
        4 -> GridShape(columns = 2, rows = 2)
        else -> GridShape(columns = 2, rows = 3)
    }

/**
 * One entry per place on the grid, in place order: the reminder there, or null for an empty
 * place. Reminders past the grid are left out. If two somehow share a place, the one stored first
 * keeps it, so the screen is the same every time.
 */
fun placesOnScreen(reminders: List<Reminder>, remindersOnScreen: Int): List<Reminder?> {
    val shape = gridShape(remindersOnScreen)
    val byPlace = HashMap<Int, Reminder>()
    for (reminder in reminders) {
        if (reminder.place in 0 until shape.places) byPlace.putIfAbsent(reminder.place, reminder)
    }
    return List(shape.places) { byPlace[it] }
}

/** The reminders that play: those on the grid. One kept past the grid is paused until the grid grows again. */
fun remindersOnScreen(reminders: List<Reminder>, remindersOnScreen: Int): List<Reminder> = placesOnScreen(reminders, remindersOnScreen).filterNotNull()

/** Reminders kept at a place past the grid, lowest place first, such as after the helper chose fewer on screen. */
fun remindersOffScreen(reminders: List<Reminder>, remindersOnScreen: Int): List<Reminder> {
    val places = gridShape(remindersOnScreen).places
    return reminders.filter { it.place >= places }.sortedBy { it.place }
}

/** True when [place] is past the grid, so whatever is there isn't shown. */
fun isOffScreen(place: Int, remindersOnScreen: Int): Boolean = place >= gridShape(remindersOnScreen).places

/** Places that are free for the reminder [editingId] (null for a new one), lowest first, out of the places on screen. */
fun placeChoices(reminders: List<Reminder>, editingId: String?, remindersOnScreen: Int): List<Int> {
    val taken = reminders.filter { it.id != editingId }.map { it.place }.toSet()
    return (0 until gridShape(remindersOnScreen).places).filter { it !in taken }
}

/** Adds or replaces [reminder] at its place. Unchanged if another reminder holds that place, or the place doesn't exist. */
fun saveReminder(reminders: List<Reminder>, reminder: Reminder): List<Reminder> {
    if (reminder.place !in 0 until MAX_PLACES) return reminders
    if (reminders.any { it.place == reminder.place && it.id != reminder.id }) return reminders
    return if (reminders.any { it.id == reminder.id }) {
        reminders.map { if (it.id == reminder.id) reminder else it }
    } else {
        reminders + reminder
    }
}

/** Removes one reminder. Its place becomes empty; nothing shifts into it. */
fun removeReminder(reminders: List<Reminder>, id: String): List<Reminder> = reminders.filterNot { it.id == id }

/** Where a place is on the grid, so settings can name it the way the helper sees it. */
enum class PlacePosition { Only, Top, Bottom, TopLeft, TopRight, MiddleLeft, MiddleRight, BottomLeft, BottomRight }

fun placePosition(place: Int, remindersOnScreen: Int): PlacePosition {
    val shape = gridShape(remindersOnScreen)
    if (shape.places == 1) return PlacePosition.Only
    if (shape.columns == 1) return if (place == 0) PlacePosition.Top else PlacePosition.Bottom
    val left = place % 2 == 0
    return when (place / 2) {
        0 -> if (left) PlacePosition.TopLeft else PlacePosition.TopRight
        shape.rows - 1 -> if (left) PlacePosition.BottomLeft else PlacePosition.BottomRight
        else -> if (left) PlacePosition.MiddleLeft else PlacePosition.MiddleRight
    }
}
