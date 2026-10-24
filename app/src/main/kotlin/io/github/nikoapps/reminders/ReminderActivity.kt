package io.github.nikoapps.reminders

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.nikoapps.reminders.reminders.Outcome
import io.github.nikoapps.reminders.reminders.current
import io.github.nikoapps.reminders.ui.AppViewModel
import io.github.nikoapps.reminders.ui.ReminderScreen
import io.github.nikoapps.reminders.ui.RemindersTheme
import io.github.nikoapps.reminders.settings.Settings
import java.time.LocalDateTime

/**
 * The reminder screen when a reminder comes due: it turns the screen on and shows over the lock
 * screen, from the reminder notification's full-screen intent. Done works without unlocking.
 * Back or Home hides it but doesn't stop the repeats; only Done does.
 */
class ReminderActivity : ComponentActivity() {
    private var reminderId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        reminderId = intent.getStringExtra(EXTRA_ID)
        setContent {
            val viewModel: AppViewModel = viewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val reminders by viewModel.reminders.collectAsStateWithLifecycle()
            val history by viewModel.history.collectAsStateWithLifecycle()
            val all = reminders ?: return@setContent
            val reminder = all.firstOrNull { it.id == reminderId }
            if (reminder == null) {
                // Removed meanwhile, or opened from an old notification.
                LaunchedEffect(Unit) { finish() }
                return@setContent
            }
            val keep = settings?.keepReminding ?: Settings().keepReminding
            val today = history.current(reminder.id, LocalDateTime.now(), keep)?.outcome
            RemindersTheme(this, settings?.theme) {
                ReminderScreen(
                    reminder = reminder,
                    today = today,
                    canDone = today == Outcome.Due || today == Outcome.Missed,
                    onPlayAgain = { viewModel.hear(reminder) },
                    onDone = {
                        viewModel.markDone(reminder)
                        finish()
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        reminderId = intent.getStringExtra(EXTRA_ID)
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        // Stays on while the reminder waits for Done, so the photo stays in view.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    companion object {
        private const val EXTRA_ID = "id"

        fun intent(context: Context, reminderId: String): Intent =
            Intent(context, ReminderActivity::class.java)
                .putExtra(EXTRA_ID, reminderId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
