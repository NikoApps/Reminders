package io.github.nikoapps.reminders.reminders

import androidx.compose.runtime.Immutable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Which days a reminder plays on. One time per reminder: a pill twice a day is two reminders. */
@Immutable
sealed interface Repeat {
    data object EveryDay : Repeat

    /** Only on these days of the week. Never empty: the editor won't save it without a day. */
    data class Days(val days: Set<DayOfWeek>) : Repeat

    /** Once, on [date]. */
    data class Once(val date: LocalDate) : Repeat
}

/**
 * One reminder: a recorded message and a photo that play at [time] on the days in [repeat].
 *
 * [place] is the reminder's fixed spot on the grid, counted from 0 at the top left, row by row.
 * It only changes when the helper moves it. A place at or past the grid size is kept but not
 * shown, and doesn't play, so making the grid smaller and then bigger brings it back.
 */
@Immutable
data class Reminder(
    val id: String,
    /** As the helper typed it, in any script, such as "White pill". Shown, read by TalkBack and spoken as it is. */
    val name: String,
    /** On the clock on the wall, to the minute. 8:00 stays 8:00 after a time zone change. */
    val time: LocalTime,
    val repeat: Repeat,
    val place: Int,
    /**
     * 0 when there's no photo. Otherwise the photo is in Reminders' own files under the id, and
     * this goes up by one each time it changes, so the tile shows the new one.
     */
    val photoVersion: Int = 0,
    /** 0 when there's no recorded message. Otherwise the recording is in Reminders' own files under the id. */
    val recordingVersion: Int = 0,
    /**
     * When the reminder was saved with this time and these days, in milliseconds since 1970.
     * Times before it never play and never count as missed, so a reminder added at 10:00 for
     * 8:00 isn't missed today.
     */
    val activeFrom: Long = 0,
) {
    val hasPhoto: Boolean get() = photoVersion > 0
    val hasRecording: Boolean get() = recordingVersion > 0
}
