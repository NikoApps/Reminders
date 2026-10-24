package io.github.nikoapps.reminders.reminders

import org.junit.Assert.assertEquals
import io.github.nikoapps.reminders.settings.KeepReminding
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class HistoryTest {
    private val today = LocalDate.of(2026, 10, 24)
    private val keep = KeepReminding.For30

    @Test
    fun `done marks today's latest time, even after it was missed`() {
        val history = listOf(
            Played("a", "Pill", today.minusDays(1).atTime(8, 0), Outcome.Missed),
            Played("a", "Pill", today.atTime(8, 0), Outcome.Missed),
        )
        val marked = history.markDone("a", today.atTime(9, 0), keep)
        assertEquals(Outcome.Missed, marked[0].outcome)
        assertEquals(Outcome.Done, marked[1].outcome)
        assertEquals(today.atTime(9, 0), marked[1].doneAt)
    }

    @Test
    fun `done with nothing today changes nothing`() {
        val history = listOf(Played("a", "Pill", today.minusDays(1).atTime(8, 0), Outcome.Missed))
        assertEquals(history, history.markDone("a", today.atTime(9, 0), keep))
        assertNull(history.current("a", today.atTime(9, 0), keep))
    }

    @Test
    fun `a reminder playing past midnight can still be marked done`() {
        val late = listOf(Played("a", "Pill", today.minusDays(1).atTime(23, 50), Outcome.Due))
        assertEquals(Outcome.Done, late.markDone("a", today.atTime(0, 10), keep).single().outcome)
        val missed = late.map { it.copy(outcome = Outcome.Missed) }
        assertEquals(missed.single(), missed.current("a", today.atTime(7, 0), keep))
    }

    @Test
    fun `done twice keeps the first time`() {
        val history = listOf(Played("a", "Pill", today.atTime(8, 0), Outcome.Due))
        val once = history.markDone("a", today.atTime(8, 3), keep)
        assertEquals(once, once.markDone("a", today.atTime(8, 9), keep))
    }
}
