package io.github.nikoapps.reminders.reminders

import io.github.nikoapps.reminders.settings.KeepReminding
import io.github.nikoapps.reminders.settings.REPEAT_EVERY_MINUTES
import java.time.LocalDate
import java.time.LocalDateTime

// When a reminder plays, on the clock on the wall. Pure, so the days and times are tested on the JVM.

/** True when the reminder plays on [date]. */
fun Reminder.playsOn(date: LocalDate): Boolean = when (val r = repeat) {
    Repeat.EveryDay -> true
    is Repeat.Days -> date.dayOfWeek in r.days
    is Repeat.Once -> date == r.date
}

/** The times the reminder plays from [from] to [to], both included, oldest first. */
fun Reminder.timesBetween(from: LocalDateTime, to: LocalDateTime): List<LocalDateTime> {
    if (to < from) return emptyList()
    return generateSequence(from.toLocalDate()) { it.plusDays(1) }
        .takeWhile { it <= to.toLocalDate() }
        .filter(::playsOn)
        .map { it.atTime(time) }
        .filter { it in from..to }
        .toList()
}

/** The first time the reminder plays after [after], or null when it never will again, such as a once reminder that's past. */
fun Reminder.nextTimeAfter(after: LocalDateTime): LocalDateTime? {
    val once = repeat as? Repeat.Once
    if (once != null) return once.date.atTime(time).takeIf { it > after }
    // Every day and chosen days come round within a week; a day more covers today's time being past.
    return generateSequence(after.toLocalDate()) { it.plusDays(1) }
        .take(9)
        .filter(::playsOn)
        .map { it.atTime(time) }
        .firstOrNull { it > after }
}

/** Every time a reminder set for [planned] plays while it waits for Done: at once, then every 5 minutes. */
fun playTimes(planned: LocalDateTime, keep: KeepReminding): List<LocalDateTime> =
    (0..keep.minutes / REPEAT_EVERY_MINUTES).map { planned.plusMinutes(it * REPEAT_EVERY_MINUTES.toLong()) }

/** The last time a reminder set for [planned] plays without Done. */
fun lastPlay(planned: LocalDateTime, keep: KeepReminding): LocalDateTime = planned.plusMinutes(keep.minutes.toLong())
