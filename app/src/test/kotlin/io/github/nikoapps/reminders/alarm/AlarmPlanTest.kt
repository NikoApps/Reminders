package io.github.nikoapps.reminders.alarm

import io.github.nikoapps.reminders.reminders.Outcome
import io.github.nikoapps.reminders.reminders.Played
import io.github.nikoapps.reminders.reminders.Reminder
import io.github.nikoapps.reminders.reminders.Repeat
import io.github.nikoapps.reminders.reminders.playTimes
import io.github.nikoapps.reminders.settings.KeepReminding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

class AlarmPlanTest {
    private val zone = ZoneOffset.UTC
    private val day = LocalDate.of(2026, 10, 24) // a Saturday
    private fun at(hour: Int, minute: Int, second: Int = 0) = day.atTime(hour, minute, second)
    private fun millis(time: LocalDateTime) = time.toInstant(zone).toEpochMilli()

    private val pill = Reminder(
        id = "pill",
        name = "White pill",
        time = LocalTime.of(8, 0),
        repeat = Repeat.EveryDay,
        place = 0,
        activeFrom = millis(day.minusDays(3).atStartOfDay()),
    )

    private fun plan(
        now: LocalDateTime,
        history: List<Played> = emptyList(),
        reminders: List<Reminder> = listOf(pill),
        keep: KeepReminding = KeepReminding.For30,
    ) = planAlarms(reminders, history, keep, now, zone)

    @Test
    fun `before its time nothing plays and the alarm is set for it`() {
        val result = plan(at(7, 0))
        assertTrue(result.playNow.isEmpty())
        assertEquals(at(8, 0), result.nextAt)
    }

    @Test
    fun `at its time it plays once and the next play is in 5 minutes`() {
        val result = plan(at(8, 0))
        assertEquals(1, result.playNow.size)
        assertEquals(Outcome.Due, result.playNow.single().outcome)
        assertEquals(1, result.playNow.single().plays)
        assertEquals(at(8, 5), result.nextAt)
    }

    @Test
    fun `an alarm a moment early still counts`() {
        val result = plan(at(7, 59, 58))
        assertEquals(1, result.playNow.size)
    }

    @Test
    fun `running the plan again at the same time doesn't play it twice`() {
        val first = plan(at(8, 0))
        val second = plan(at(8, 0, 30), first.history)
        assertTrue(second.playNow.isEmpty())
        assertEquals(at(8, 5), second.nextAt)
    }

    @Test
    fun `without Done it plays seven times in 30 minutes, then is missed`() {
        var history = emptyList<Played>()
        var plays = 0
        var missed = 0
        var now = at(8, 0)
        while (now <= at(9, 0)) {
            val result = plan(now, history)
            plays += result.playNow.size
            missed += result.missedNow.size
            history = result.history
            now = now.plusMinutes(1)
        }
        assertEquals(7, plays)
        assertEquals(1, missed)
        assertEquals(Outcome.Missed, history.single().outcome)
    }

    @Test
    fun `the last play says it's missed`() {
        var history = plan(at(8, 0)).history
        listOf(5, 10, 15, 20, 25).forEach { history = plan(at(8, it), history).history }
        val last = plan(at(8, 30), history)
        assertEquals(Outcome.Missed, last.playNow.single().outcome)
        assertEquals(last.playNow, last.missedNow)
    }

    @Test
    fun `after Done it stops repeating and the next alarm is tomorrow`() {
        val first = plan(at(8, 0))
        val done = first.history.map { it.copy(outcome = Outcome.Done, doneAt = at(8, 2)) }
        val result = plan(at(8, 5), done)
        assertTrue(result.playNow.isEmpty())
        assertEquals(day.plusDays(1).atTime(8, 0), result.nextAt)
    }

    @Test
    fun `a reminder before midnight keeps playing after it`() {
        val late = pill.copy(time = LocalTime.of(23, 50))
        var history = plan(at(23, 50), reminders = listOf(late)).history
        val after = plan(day.plusDays(1).atTime(0, 0), history, reminders = listOf(late))
        assertEquals(1, after.playNow.size)
        assertEquals(day.plusDays(1).atTime(0, 5), after.nextAt)
    }

    @Test
    fun `a phone back on after midnight still plays one from before it`() {
        val late = pill.copy(time = LocalTime.of(23, 58))
        val result = plan(day.plusDays(1).atTime(0, 10), reminders = listOf(late))
        assertEquals(day.atTime(23, 58), result.playNow.single().planned)
    }

    @Test
    fun `don't repeat still plays when the phone was a few minutes late`() {
        val result = plan(at(8, 10), keep = KeepReminding.Off)
        assertEquals(1, result.playNow.size)
    }

    @Test
    fun `don't repeat plays once and is missed straight after`() {
        val result = plan(at(8, 0), keep = KeepReminding.Off)
        assertEquals(Outcome.Missed, result.playNow.single().outcome)
    }

    @Test
    fun `a phone back on within the keep reminding time plays then`() {
        val result = plan(at(8, 17))
        assertEquals(1, result.playNow.size)
        assertEquals(4, result.playNow.single().plays)
        assertEquals(at(8, 20), result.nextAt)
    }

    @Test
    fun `a phone off for the whole time shows it missed without playing`() {
        val result = plan(at(11, 0))
        assertTrue(result.playNow.isEmpty())
        assertEquals(Outcome.Missed, result.missedNow.single().outcome)
    }

    @Test
    fun `a reminder added after its time today isn't missed today`() {
        val added = pill.copy(activeFrom = millis(at(10, 0)))
        val result = plan(at(10, 0), reminders = listOf(added))
        assertTrue(result.playNow.isEmpty())
        assertTrue(result.history.isEmpty())
        assertEquals(day.plusDays(1).atTime(8, 0), result.nextAt)
    }

    @Test
    fun `a reminder changed while it waits for Done stops waiting without counting as missed`() {
        val first = plan(at(8, 0))
        val changed = pill.copy(time = LocalTime.of(9, 0), activeFrom = millis(at(8, 3)))
        val result = plan(at(8, 5), first.history, reminders = listOf(changed))
        assertTrue(result.playNow.isEmpty())
        assertTrue(result.history.isEmpty())
        assertEquals(at(9, 0), result.nextAt)
    }

    @Test
    fun `a removed reminder stops waiting`() {
        val first = plan(at(8, 0))
        val result = plan(at(8, 5), first.history, reminders = emptyList())
        assertTrue(result.playNow.isEmpty())
        assertNull(result.nextAt)
    }

    @Test
    fun `two reminders at the same time play one after the other, in place order`() {
        val prayer = pill.copy(id = "prayer", name = "Prayer", place = 1)
        val result = plan(at(8, 0), reminders = listOf(pill, prayer))
        assertEquals(listOf("pill", "prayer"), result.playNow.map { it.reminderId })
    }

    @Test
    fun `chosen days only play on those days`() {
        val weekdays = pill.copy(repeat = Repeat.Days(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)))
        val saturday = plan(at(8, 0), reminders = listOf(weekdays))
        assertTrue(saturday.playNow.isEmpty())
        assertEquals(LocalDate.of(2026, 10, 26).atTime(8, 0), saturday.nextAt)
    }

    @Test
    fun `a once reminder plays on its date and never again`() {
        val once = pill.copy(repeat = Repeat.Once(day))
        val result = plan(at(8, 0), reminders = listOf(once))
        assertEquals(1, result.playNow.size)
        val done = result.history.map { it.copy(outcome = Outcome.Done) }
        val after = plan(at(8, 1), done, reminders = listOf(once))
        assertNull(after.nextAt)
    }

    @Test
    fun `a missed notification is cleared at midnight`() {
        val missed = plan(at(11, 0))
        val result = plan(at(11, 1), missed.history, reminders = listOf(pill.copy(repeat = Repeat.Once(day))))
        assertEquals(day.plusDays(1).atStartOfDay(), result.nextAt)
    }

    @Test
    fun `history older than a week goes`() {
        val old = Played("pill", "White pill", day.minusDays(7).atTime(8, 0), Outcome.Done)
        val recent = Played("pill", "White pill", day.minusDays(6).atTime(8, 0), Outcome.Done)
        val result = plan(at(7, 0), listOf(old, recent))
        assertEquals(listOf(recent), result.history)
    }

    @Test
    fun `play times are every 5 minutes for the keep reminding time`() {
        assertEquals(1, playTimes(at(8, 0), KeepReminding.Off).size)
        assertEquals(4, playTimes(at(8, 0), KeepReminding.For15).size)
        assertEquals(7, playTimes(at(8, 0), KeepReminding.For30).size)
        assertEquals(13, playTimes(at(8, 0), KeepReminding.For60).size)
        assertEquals(at(8, 30), playTimes(at(8, 0), KeepReminding.For30).last())
    }
}
