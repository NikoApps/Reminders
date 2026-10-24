package io.github.nikoapps.reminders.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Runs when the reminder alarm fires, and puts it back after a restart, an app update, a clock or
 * time zone change, and when the exact alarm permission changes. After a restart that's before
 * the first unlock, so a locked phone keeps reminding (Hub ADR 0036).
 *
 * Doesn't play anything itself: Android may stop audio from a receiver, so [Alarms] starts
 * [ReminderService].
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                Alarms.refresh(app)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_ALARM = "io.github.nikoapps.reminders.action.ALARM"

        private val ACTIONS = setOf(
            ACTION_ALARM,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
        )
    }
}
