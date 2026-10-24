package io.github.nikoapps.reminders.reminders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class ReminderEditorTest {
    private val today = LocalDate.of(2026, 10, 24)
    private val now = today.atTime(10, 0)

    @Test
    fun `a name is needed`() {
        assertEquals(listOf(EditorProblem.NoName), editorProblems("  ", RepeatKind.EveryDay, emptySet(), today, LocalTime.of(8, 0), now))
    }

    @Test
    fun `chosen days need a day`() {
        assertEquals(listOf(EditorProblem.NoDays), editorProblems("Pill", RepeatKind.ChosenDays, emptySet(), today, LocalTime.of(8, 0), now))
    }

    @Test
    fun `once can't be in the past`() {
        assertEquals(listOf(EditorProblem.Past), editorProblems("Pill", RepeatKind.Once, emptySet(), today, LocalTime.of(8, 0), now))
        assertTrue(editorProblems("Pill", RepeatKind.Once, emptySet(), today, LocalTime.of(11, 0), now).isEmpty())
    }

    @Test
    fun `same time finds reminders that meet on a day`() {
        val pill = Reminder("a", "Pill", LocalTime.of(8, 0), Repeat.Days(setOf(DayOfWeek.MONDAY)), 0)
        val prayer = Reminder("b", "Prayer", LocalTime.of(8, 0), Repeat.Days(setOf(DayOfWeek.TUESDAY)), 1)
        val all = listOf(pill, prayer)
        assertEquals(listOf(pill), sameTime(all, null, LocalTime.of(8, 0), Repeat.EveryDay, today).filter { it.id == "a" })
        assertTrue(sameTime(all, "b", LocalTime.of(8, 0), Repeat.Days(setOf(DayOfWeek.TUESDAY)), today).isEmpty())
        assertTrue(sameTime(all, null, LocalTime.of(9, 0), Repeat.EveryDay, today).isEmpty())
    }
}
