package io.github.nikoapps.reminders.reminders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class RemindersCodecTest {

    @Test
    fun `reminders read back as they were, in any script`() {
        val reminders = listOf(
            Reminder("6f1c2a9e", "White pill; with water, after food", LocalTime.of(8, 0), Repeat.EveryDay, 0, 2, 1, 123L),
            Reminder("b", "नमाज़", LocalTime.of(13, 45), Repeat.Days(setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY)), 3),
            Reminder("c", "Doctor", LocalTime.of(23, 59), Repeat.Once(LocalDate.of(2026, 11, 2)), 5),
        )
        assertEquals(reminders, RemindersCodec.decode(RemindersCodec.encode(reminders)))
    }

    @Test
    fun `a broken record is skipped and the rest kept`() {
        val good = Reminder("a", "Pill", LocalTime.of(8, 0), Repeat.EveryDay, 0)
        val text = RemindersCodec.encode(listOf(good)) + ",garbage;x"
        assertEquals(listOf(good), RemindersCodec.decode(text))
        assertTrue(RemindersCodec.decode(null).isEmpty())
    }

    @Test
    fun `repeats are short codes`() {
        assertEquals("d", RemindersCodec.encodeRepeat(Repeat.EveryDay))
        assertEquals("w15", RemindersCodec.encodeRepeat(Repeat.Days(setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY))))
        assertEquals("o2026-10-24", RemindersCodec.encodeRepeat(Repeat.Once(LocalDate.of(2026, 10, 24))))
        assertEquals(null, RemindersCodec.decodeRepeat("w"))
    }

    @Test
    fun `history reads back as it was`() {
        val history = listOf(
            Played("a", "Pill, white", LocalDateTime.of(2026, 10, 24, 8, 0), Outcome.Done, 2, LocalDateTime.of(2026, 10, 24, 8, 6)),
            Played("b", "Prayer", LocalDateTime.of(2026, 10, 24, 13, 0), Outcome.Missed, 7),
        )
        assertEquals(history, HistoryCodec.decode(HistoryCodec.encode(history)))
    }
}
