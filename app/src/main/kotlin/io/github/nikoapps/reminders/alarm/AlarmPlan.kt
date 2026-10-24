package io.github.nikoapps.reminders.alarm

import io.github.nikoapps.reminders.reminders.Outcome
import io.github.nikoapps.reminders.reminders.Played
import io.github.nikoapps.reminders.reminders.Reminder
import io.github.nikoapps.reminders.reminders.keptOn
import io.github.nikoapps.reminders.reminders.lastPlay
import io.github.nikoapps.reminders.reminders.nextTimeAfter
import io.github.nikoapps.reminders.reminders.playTimes
import io.github.nikoapps.reminders.reminders.timesBetween
import io.github.nikoapps.reminders.settings.KeepReminding
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * What to do now, worked out from the reminders and what has already played. Every alarm, restart,
 * clock change and app start runs the same plan, so a reminder plays once at each time however
 * often it's run, and one that came due while nothing was running still plays.
 *
 * [history] is the history to save. [playNow] are the reminders to play now, oldest first; one
 * whose [Played.outcome] is [Outcome.Missed] is playing for the last time. [missedNow] became
 * missed in this run, so their notification says so. [nextAt] is when to run the plan again, or
 * null when nothing is coming.
 */
data class AlarmPlan(
    val history: List<Played>,
    val playNow: List<Played>,
    val missedNow: List<Played>,
    val nextAt: LocalDateTime?,
)

/** An alarm can fire a moment before its time; it still counts as that time. */
private const val EARLY_SECONDS = 5L

/**
 * A play that comes this late still plays, such as when the phone was busy or restarting. Later
 * than this, it counts as missed. A reminder that doesn't repeat gets longer, since its one play
 * is all there is.
 */
private fun lateMinutes(keep: KeepReminding): Long = if (keep == KeepReminding.Off) 15L else 2L

/**
 * [reminders] are the ones that play: those on the grid, in place order. [now] and [zone] are the
 * clock on the wall.
 */
fun planAlarms(
    reminders: List<Reminder>,
    history: List<Played>,
    keep: KeepReminding,
    now: LocalDateTime,
    zone: ZoneId,
): AlarmPlan {
    val at = now.plusSeconds(EARLY_SECONDS)
    val late = lateMinutes(keep)
    val today = now.toLocalDate()
    val byId = reminders.associateBy { it.id }
    fun Reminder.from(): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(activeFrom), zone)

    // A reminder removed, moved off the grid or changed while it waited for Done stops waiting.
    // It isn't counted as missed: the helper changed it.
    var kept = history.keptOn(today).filterNot { played ->
        val reminder = byId[played.reminderId]
        played.outcome == Outcome.Due && (reminder == null || played.planned < reminder.from())
    }

    val missed = mutableListOf<Played>()
    // Times that came due since the last run: today, and any from before midnight still in their
    // keep reminding time. Never before the reminder was set.
    val lookBack = minOf(today.atStartOfDay(), at.minusMinutes(keep.minutes + late))
    for (reminder in reminders) {
        val from = maxOf(lookBack, reminder.from())
        for (time in reminder.timesBetween(from, at)) {
            if (kept.any { it.isFor(reminder.id, time) }) continue
            val last = playTimes(time, keep).last()
            kept = kept + if (at <= last.plusMinutes(late)) {
                Played(reminder.id, reminder.name, time, Outcome.Due)
            } else {
                // The phone was off or asleep through the whole keep reminding time.
                Played(reminder.id, reminder.name, time, Outcome.Missed).also(missed::add)
            }
        }
    }

    val play = mutableListOf<Played>()
    kept = kept.map { played ->
        if (played.outcome != Outcome.Due) return@map played
        val times = playTimes(played.planned, keep)
        if (at > times.last().plusMinutes(late)) {
            return@map played.copy(outcome = Outcome.Missed).also(missed::add)
        }
        val due = times.count { it <= at }
        if (due <= played.plays) return@map played
        // Plays once now, however many plays it's behind, then carries on from here.
        val outcome = if (due >= times.size) Outcome.Missed else Outcome.Due
        played.copy(plays = due, outcome = outcome).also {
            play += it
            if (outcome == Outcome.Missed) missed += it
        }
    }

    val next = buildList {
        kept.filter { it.outcome == Outcome.Due }.forEach { played ->
            playTimes(played.planned, keep).firstOrNull { it > at }?.let(::add)
        }
        reminders.forEach { reminder -> reminder.nextTimeAfter(at)?.let(::add) }
        // A missed notification lasts until the day after the reminder's last play.
        kept.filter { it.outcome == Outcome.Missed }
            .map { lastPlay(it.planned, keep).toLocalDate().plusDays(1).atStartOfDay() }
            .filter { it > at }
            .forEach(::add)
    }.minOrNull()

    val places = reminders.mapIndexed { index, reminder -> reminder.id to index }.toMap()
    val order = compareBy<Played>({ it.planned }, { places[it.reminderId] ?: Int.MAX_VALUE })
    return AlarmPlan(history = kept, playNow = play.sortedWith(order), missedNow = missed.sortedWith(order), nextAt = next)
}
