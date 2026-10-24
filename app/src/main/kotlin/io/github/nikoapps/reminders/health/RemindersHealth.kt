package io.github.nikoapps.reminders.health

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.getSystemService
import io.github.nikoapps.commons.health.HealthCheck
import io.github.nikoapps.commons.health.PhoneHealth
import io.github.nikoapps.reminders.R
import io.github.nikoapps.reminders.alarm.ReminderNotifications

/**
 * The phone settings that stop a reminder playing on time or showing over the lock screen. A
 * reminder plays on time or the app says plainly why it can't. The shared checks and their fixes
 * live in Commons. Voice problems come from the speech engine, so they're shown next to these
 * with Commons' voice row rather than as a check here.
 */
object RemindersHealth {

    /** Android 12 and newer: exact alarms aren't allowed, so reminders may come late. */
    val ExactAlarms = HealthCheck(
        title = R.string.health_exact_alarms,
        fix = R.string.health_exact_alarms_value,
        isProblem = { context ->
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                context.getSystemService<AlarmManager>()?.canScheduleExactAlarms() == false
        },
        fixIntent = { context -> Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, PhoneHealth.packageUri(context)) },
    )

    /**
     * Notifications are off, so the reminder screen can't come up and nothing says a reminder
     * was missed. The app asks for the permission itself when this is fixed, and opens the
     * notification settings once the phone won't ask again.
     */
    val Notifications = HealthCheck(
        title = R.string.health_notifications,
        fix = R.string.health_notifications_value,
        isProblem = { context -> !ReminderNotifications.canShow(context) },
        fixIntent = { context ->
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        },
    )

    /** Android 14 and newer: full-screen reminders are off, so a reminder plays but doesn't show over the lock screen. */
    val FullScreen = HealthCheck(
        title = R.string.health_full_screen,
        fix = R.string.health_full_screen_value,
        isProblem = { context -> !ReminderNotifications.canUseFullScreen(context) },
        fixIntent = { context ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, PhoneHealth.packageUri(context))
            } else {
                PhoneHealth.appInfo(context)
            }
        },
    )

    /** The alarm volume is at zero, so nobody hears a reminder. Reminders play at their own share of it. */
    val AlarmVolume = HealthCheck(
        title = R.string.health_alarm_volume,
        fix = R.string.health_alarm_volume_value,
        isProblem = { context ->
            val audio = context.getSystemService<AudioManager>()
            audio != null && audio.getStreamVolume(AudioManager.STREAM_ALARM) == 0
        },
        fixIntent = { Intent(Settings.ACTION_SOUND_SETTINGS) },
    )

    /**
     * What's wrong on this phone now, in the order the hint line and settings show them: alarms
     * first, then showing, then the rest. Only while there's at least one reminder; with none,
     * nothing needs to run. Run again each time the app comes to the front.
     */
    fun problems(context: Context, hasReminders: Boolean): List<HealthCheck> {
        if (!hasReminders) return emptyList()
        return PhoneHealth.problems(
            context,
            listOf(ExactAlarms, Notifications, FullScreen, AlarmVolume, PhoneHealth.Battery, PhoneHealth.Hibernation),
        )
    }
}
