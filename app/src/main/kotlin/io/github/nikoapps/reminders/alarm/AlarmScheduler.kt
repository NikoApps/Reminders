package io.github.nikoapps.reminders.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.getSystemService
import io.github.nikoapps.reminders.MainActivity
import java.time.ZonedDateTime

/**
 * One alarm at a time: the next reminder, or the next play of one waiting for Done, whichever
 * comes first. It's a `setAlarmClock` alarm, which Android never moves and which wakes the phone
 * from its deepest sleep (spec decision 1). Android shows it as the phone's next alarm.
 */
object AlarmScheduler {

    fun schedule(context: Context, at: ZonedDateTime?) {
        if (at == null) return cancel(context)
        val alarms = context.getSystemService<AlarmManager>() ?: return
        val millis = at.toInstant().toEpochMilli()
        val operation = alarmIntent(context, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
            try {
                alarms.setAlarmClock(AlarmManager.AlarmClockInfo(millis, showIntent(context)), operation)
                return
            } catch (_: SecurityException) {
                // The permission went away between the check and the call. Fall through.
            }
        }
        // Only if a helper took the exact alarm permission away: late is better than silent, and
        // the health check says so.
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, operation)
    }

    fun cancel(context: Context) {
        val alarms = context.getSystemService<AlarmManager>() ?: return
        alarmIntent(context, PendingIntent.FLAG_NO_CREATE)?.let {
            alarms.cancel(it)
            it.cancel()
        }
    }

    private fun alarmIntent(context: Context, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_ALARM),
            flags or PendingIntent.FLAG_IMMUTABLE,
        )

    /** What opens when someone taps the next alarm in the phone's clock or status bar. */
    private fun showIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
