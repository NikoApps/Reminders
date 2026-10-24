package io.github.nikoapps.reminders.alarm

import android.content.Context
import android.util.Log
import io.github.nikoapps.reminders.reminders.Outcome
import io.github.nikoapps.reminders.reminders.RemindersRepository
import io.github.nikoapps.reminders.reminders.markDone
import io.github.nikoapps.reminders.reminders.remindersOnScreen
import io.github.nikoapps.reminders.settings.SettingsRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Brings alarms, notifications and playing in line with the reminders: records what came due,
 * starts playing it, and schedules the next alarm. Safe to run any time and as often as
 * needed: after each alarm, a restart or clock change, when the app comes to the front, and
 * after the helper changes a reminder or a setting.
 */
object Alarms {
    private const val TAG = "Alarms"
    private val lock = Mutex()

    suspend fun refresh(context: Context) = lock.withLock {
        val app = context.applicationContext
        val settings = SettingsRepository(app).current()
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        val plan = RemindersRepository(app).withHistory { reminders, history ->
            val plan = planAlarms(remindersOnScreen(reminders, settings.remindersOnScreen), history, settings.keepReminding, now, zone)
            plan.history to plan
        }
        AlarmScheduler.schedule(app, plan.nextAt?.let { atZone(it, zone) })

        // A reminder playing for the last time says it's missed once it has played.
        val playing = plan.playNow.map { it.reminderId }.toSet()
        plan.missedNow.filter { it.reminderId !in playing }.forEach { ReminderNotifications.showMissed(app, it) }
        ReminderNotifications.sync(app, plan.history, now.toLocalDate(), settings.keepReminding)

        if (plan.playNow.isNotEmpty()) {
            try {
                ReminderService.play(app, plan.playNow)
            } catch (e: IllegalStateException) {
                // ForegroundServiceStartNotAllowedException, such as from a late alarm when exact
                // alarms aren't allowed. The notification still brings the reminder screen up.
                Log.w(TAG, "Couldn't start playing", e)
                plan.playNow.forEach {
                    if (it.outcome == Outcome.Missed) ReminderNotifications.showMissed(app, it) else ReminderNotifications.showDue(app, it)
                }
            }
        }
    }

    /**
     * The moment for a time on the clock on the wall. In the hour that repeats when the clocks go
     * back, the later one if the earlier is already past, so the alarm doesn't keep firing at once.
     */
    private fun atZone(time: LocalDateTime, zone: ZoneId): ZonedDateTime {
        val zoned = time.atZone(zone)
        return if (zoned.toInstant() <= Instant.now()) zoned.withLaterOffsetAtOverlap() else zoned
    }

    /** Done for today's latest time of reminder [id]: it stops playing and the tile says Done. */
    suspend fun markDone(context: Context, id: String) {
        val app = context.applicationContext
        val keep = SettingsRepository(app).current().keepReminding
        RemindersRepository(app).updateHistory { it.markDone(id, LocalDateTime.now(), keep) }
        ReminderNotifications.cancel(app, id)
        refresh(app)
    }
}
