package io.github.nikoapps.reminders.reminders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class ScheduleTest {
    private val saturday = LocalDate.of(2026, 10, 24)
    private fun reminder(repeat: Repeat) = Reminder("a", "Pill", LocalTime.of(8, 0), repeat, 0)

    @Test
    fun `every day plays every day`() {
        assertTrue(reminder(Repeat.EveryDay).playsOn(saturday))
        assertTrue(reminder(Repeat.EveryDay).playsOn(saturday.plusDays(3)))
    }

    @Test
    fun `chosen days play only on those days`() {
        val r = reminder(Repeat.Days(setOf(DayOfWeek.SATURDAY)))
        assertTrue(r.playsOn(saturday))
        assertFalse(r.playsOn(saturday.plusDays(1)))
    }

    @Test
    fun `next time is later today, or the next day it plays`() {
        val r = reminder(Repeat.Days(setOf(DayOfWeek.MONDAY)))
        assertEquals(LocalDate.of(2026, 10, 26).atTime(8, 0), r.nextTimeAfter(saturday.atTime(7, 0)))
        assertEquals(saturday.atTime(8, 0), reminder(Repeat.EveryDay).nextTimeAfter(saturday.atTime(7, 59)))
        assertEquals(saturday.plusDays(1).atTime(8, 0), reminder(Repeat.EveryDay).nextTimeAfter(saturday.atTime(8, 0)))
    }

    @Test
    fun `a past once reminder has no next time`() {
        assertNull(reminder(Repeat.Once(saturday)).nextTimeAfter(saturday.atTime(9, 0)))
        assertEquals(saturday.atTime(8, 0), reminder(Repeat.Once(saturday)).nextTimeAfter(saturday.atTime(7, 0)))
    }

    @Test
    fun `times between include both ends`() {
        val times = reminder(Repeat.EveryDay).timesBetween(saturday.atTime(8, 0), saturday.plusDays(2).atTime(8, 0))
        assertEquals(3, times.size)
        assertTrue(reminder(Repeat.EveryDay).timesBetween(saturday.atTime(9, 0), saturday.atTime(8, 0)).isEmpty())
    }
}
