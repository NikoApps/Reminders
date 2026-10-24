package io.github.nikoapps.reminders.reminders

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

// The editor's rules, without Android, so they're tested on the JVM.

/** What stops a reminder being saved. */
enum class EditorProblem {
    /** The tile needs a name, and the voice says it when there's no recording. */
    NoName,

    /** Chosen days with no day chosen. */
    NoDays,

    /** Once, at a time that's already past. */
    Past,
}

/** How the reminder repeats, as the editor's choice shows it. */
enum class RepeatKind { EveryDay, ChosenDays, Once }

fun Repeat.kind(): RepeatKind = when (this) {
    Repeat.EveryDay -> RepeatKind.EveryDay
    is Repeat.Days -> RepeatKind.ChosenDays
    is Repeat.Once -> RepeatKind.Once
}

/** The repeat for the editor's [kind], with its chosen [days] or [date]. */
fun repeatOf(kind: RepeatKind, days: Set<DayOfWeek>, date: LocalDate): Repeat = when (kind) {
    RepeatKind.EveryDay -> Repeat.EveryDay
    RepeatKind.ChosenDays -> Repeat.Days(days)
    RepeatKind.Once -> Repeat.Once(date)
}

fun editorProblems(name: String, kind: RepeatKind, days: Set<DayOfWeek>, date: LocalDate, time: LocalTime, now: LocalDateTime): List<EditorProblem> =
    buildList {
        if (name.isBlank()) add(EditorProblem.NoName)
        if (kind == RepeatKind.ChosenDays && days.isEmpty()) add(EditorProblem.NoDays)
        if (kind == RepeatKind.Once && date.atTime(time) <= now) add(EditorProblem.Past)
    }

/**
 * Other reminders on screen that play at the same time on at least one day, so the editor can
 * say both will play, one after the other. Once reminders that are past don't count.
 */
fun sameTime(reminders: List<Reminder>, editingId: String?, time: LocalTime, repeat: Repeat, today: LocalDate): List<Reminder> {
    val days = upcomingDays(repeat, today)
    return reminders.filter { other ->
        other.id != editingId && other.time == time && upcomingDays(other.repeat, today).any { it in days }
    }
}

/** The days in the next week, or the one date, a repeat plays on. Two weekly repeats meet within a week. */
private fun upcomingDays(repeat: Repeat, today: LocalDate): Set<LocalDate> = when (repeat) {
    is Repeat.Once -> setOf(repeat.date)
    else -> (0L..6L).map { today.plusDays(it) }.filter { date ->
        repeat == Repeat.EveryDay || (repeat is Repeat.Days && date.dayOfWeek in repeat.days)
    }.toSet()
}
