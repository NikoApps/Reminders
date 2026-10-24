package io.github.nikoapps.reminders.reminders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

class PlacesTest {
    private fun r(id: String, place: Int) = Reminder(id, id, LocalTime.of(8, 0), Repeat.EveryDay, place)

    @Test
    fun `removing one never moves another`() {
        val all = listOf(r("a", 0), r("b", 1), r("c", 2))
        val after = removeReminder(all, "b")
        val places = placesOnScreen(after, 4)
        assertEquals("a", places[0]?.id)
        assertNull(places[1])
        assertEquals("c", places[2]?.id)
    }

    @Test
    fun `saving into a taken place changes nothing`() {
        val all = listOf(r("a", 0), r("b", 1))
        assertEquals(all, saveReminder(all, r("c", 1)))
        assertEquals(all, saveReminder(all, r("c", 6)))
    }

    @Test
    fun `moving a reminder to a free place moves only it`() {
        val all = listOf(r("a", 0), r("b", 1))
        val moved = saveReminder(all, r("a", 3))
        assertEquals(listOf(3, 1), moved.map { it.place })
    }

    @Test
    fun `a smaller grid keeps reminders past it and they don't play`() {
        val all = listOf(r("a", 0), r("b", 3))
        assertEquals(listOf("a"), remindersOnScreen(all, 2).map { it.id })
        assertEquals(listOf("b"), remindersOffScreen(all, 2).map { it.id })
        assertEquals(listOf("a", "b"), remindersOnScreen(all, 4).map { it.id })
    }

    @Test
    fun `place choices are the free places on screen`() {
        val all = listOf(r("a", 0), r("b", 1))
        assertEquals(listOf(2, 3), placeChoices(all, null, 4))
        assertEquals(listOf(0, 2, 3), placeChoices(all, "a", 4))
    }

    @Test
    fun `places are named as the helper sees them`() {
        assertEquals(PlacePosition.Only, placePosition(0, 1))
        assertEquals(PlacePosition.Bottom, placePosition(1, 2))
        assertEquals(PlacePosition.TopRight, placePosition(1, 4))
        assertEquals(PlacePosition.MiddleLeft, placePosition(2, 6))
        assertEquals(PlacePosition.BottomRight, placePosition(5, 6))
    }
}
