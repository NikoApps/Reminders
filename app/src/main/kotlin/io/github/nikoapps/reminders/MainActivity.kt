package io.github.nikoapps.reminders

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.nikoapps.commons.health.HealthCheck
import io.github.nikoapps.commons.health.PhoneHealth
import io.github.nikoapps.commons.ui.SetPinScreen
import io.github.nikoapps.commons.ui.UnlockPinScreen
import io.github.nikoapps.commons.ui.openPage
import io.github.nikoapps.reminders.health.RemindersHealth
import io.github.nikoapps.reminders.reminders.Outcome
import io.github.nikoapps.reminders.reminders.gridShape
import io.github.nikoapps.reminders.reminders.placesOnScreen
import io.github.nikoapps.reminders.reminders.current
import io.github.nikoapps.reminders.settings.RemindersHelperPin
import io.github.nikoapps.reminders.ui.AppViewModel
import io.github.nikoapps.reminders.ui.EditorScreen
import io.github.nikoapps.reminders.ui.MainScreen
import io.github.nikoapps.reminders.ui.ReminderEdit
import io.github.nikoapps.reminders.ui.ReminderScreen
import io.github.nikoapps.reminders.ui.RemindersTheme
import io.github.nikoapps.reminders.ui.Screen
import io.github.nikoapps.reminders.ui.SettingsScreen
import io.github.nikoapps.reminders.ui.SetupScreen
import io.github.nikoapps.reminders.ui.Tile
import io.github.nikoapps.reminders.ui.hintFor
import io.github.nikoapps.reminders.ui.hintText
import io.github.nikoapps.reminders.ui.screenAfterLeaving
import io.github.nikoapps.reminders.ui.screenAfterRestart
import java.time.LocalDateTime

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: AppViewModel = viewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            RemindersTheme(this, settings?.theme) {
                App(viewModel)
            }
        }
    }

    /**
     * True from when Reminders opens another app's screen itself, such as the camera, the photo
     * picker, a permission question or a phone settings page, until Reminders is back in front. A
     * helper doing that is still in the middle of something, so the PIN isn't asked again.
     */
    internal var openedAnotherScreen = false
        private set

    // Every startActivity and every activity result launcher ends up here.
    @Deprecated("Deprecated in ComponentActivity")
    @Suppress("DEPRECATION")
    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        openedAnotherScreen = true
        try {
            super.startActivityForResult(intent, requestCode, options)
        } catch (e: RuntimeException) {
            // Nothing opened, such as no app for it, so Reminders never left.
            openedAnotherScreen = false
            throw e
        }
    }

    override fun onResume() {
        super.onResume()
        openedAnotherScreen = false
    }
}

@Composable
private fun App(viewModel: AppViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val unreadable by viewModel.unreadable.collectAsStateWithLifecycle()
    val voice by viewModel.voice.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.Main) }
    var setupStep by rememberSaveable { mutableIntStateOf(0) }
    var pinReturn by rememberSaveable { mutableStateOf(Screen.Settings) }
    var editorReturn by rememberSaveable { mutableStateOf(Screen.Settings) }
    var editPlace by rememberSaveable { mutableIntStateOf(0) }
    var openReminderId by rememberSaveable { mutableStateOf<String?>(null) }
    // After Android stopped Reminders in the background, the saved screen comes back but the PIN
    // doesn't count any more. Checked once for each new view model, as soon as the PIN is known.
    if (!viewModel.screenRestored) {
        settings?.let {
            viewModel.screenRestored = true
            screen = screenAfterRestart(screen, pinSet = it.helperPinHash != null)
        }
    }

    val context = LocalContext.current
    val activity = LocalActivity.current

    // Health checks run again each time Reminders comes back to the front, such as after fixing a
    // setting in the phone's settings, and after a permission question closes.
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    var healthCheckRun by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onResume()
        healthCheckRun++
    }
    // Leaving Reminders, such as pressing Home: with a PIN set, the next person to open it starts
    // at the grid, not in the helper's screens. Turning the phone keeps everything, and so does a
    // trip to a screen Reminders opened itself, such as the camera.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        val main = activity as? MainActivity
        val changingConfig = activity?.isChangingConfigurations == true
        if (!changingConfig) viewModel.stopHearing()
        screen = screenAfterLeaving(
            current = screen,
            pinSet = settings?.helperPinHash != null,
            changingConfig = changingConfig,
            openedAnotherScreen = main?.openedAnotherScreen == true,
        )
    }

    // Notifications bring the reminder screen and say when one was missed. Asked in first run or
    // when the first reminder is saved. Once the phone won't ask again, the notification settings
    // page is the only place to allow them.
    var notificationsBlocked by rememberSaveable { mutableStateOf(false) }
    var notificationsAsked by rememberSaveable { mutableStateOf(false) }
    val askForNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsBlocked = !granted && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
        healthCheckRun++
    }
    val canAskForNotifications = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    val allowNotifications = {
        notificationsAsked = true
        if (canAskForNotifications && !notificationsBlocked) {
            askForNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openPage(context, RemindersHealth.Notifications.fixIntent(context), PhoneHealth.appInfo(context))
        }
    }

    BackHandler(enabled = screen != Screen.Main) {
        if (screen == Screen.Reminder) viewModel.stopHearing()
        screen = when (screen) {
            Screen.SetPin -> pinReturn
            Screen.EditReminder -> editorReturn
            else -> Screen.Main
        }
    }

    val current = settings ?: return
    val everyone = reminders ?: return
    val now = LocalDateTime.now()
    val onScreen = remember(everyone, current.remindersOnScreen) { placesOnScreen(everyone, current.remindersOnScreen) }
    val problems = remember(lifecycle, healthCheckRun, everyone.isNotEmpty()) { RemindersHealth.problems(context, everyone.isNotEmpty()) }
    // Setup shows the phone settings before the first reminder is added, so the helper can do it all in one visit.
    val setupProblems = remember(lifecycle, healthCheckRun) { RemindersHealth.problems(context, hasReminders = true) }
    val voiceNeeded = onScreen.any { it != null && !it.hasRecording }

    // First run is for the helper. A phone that already has a PIN is past that, and skipping the
    // PIN for setup would let anyone into settings.
    LaunchedEffect(Unit) {
        if (!current.setupDone && current.helperPinHash == null && screen == Screen.Main) screen = Screen.Setup
    }
    val finishSetup = {
        viewModel.update { it.copy(setupDone = true) }
        setupStep = 0
        screen = Screen.Main
    }
    val editReminder = { place: Int, from: Screen ->
        editPlace = place
        editorReturn = from
        screen = Screen.EditReminder
    }
    val removePin = { viewModel.update { it.copy(helperPinHash = null) } }
    // Problems the app fixes itself; the rest open the phone's page that fixes them.
    val fixProblem: (HealthCheck) -> Unit = { check ->
        when {
            check === RemindersHealth.Notifications -> allowNotifications()
            else -> openPage(context, check.fixIntent(context), PhoneHealth.appInfo(context))
        }
    }
    val saveReminder = { edit: ReminderEdit ->
        viewModel.saveReminder(edit)
        // The first reminder saved asks for notifications, if first run didn't.
        if (canAskForNotifications && !notificationsAsked) allowNotifications()
    }

    when (screen) {
        Screen.Main -> MainScreen(
            tiles = onScreen.map { reminder -> reminder?.let { Tile(it, history.current(it.id, now, current.keepReminding)?.outcome) } },
            columns = gridShape(current.remindersOnScreen).columns,
            hint = hintText(
                hintFor(
                    hasReminders = onScreen.any { it != null },
                    unreadable = unreadable,
                    problems = problems,
                    voice = voice.voice,
                    voiceNeeded = voiceNeeded,
                ),
                voice.voice,
            ),
            onOpen = { reminder ->
                openReminderId = reminder.id
                screen = Screen.Reminder
                // Tapping a tile plays the message, so the person can hear what a picture is for at any time.
                viewModel.hear(reminder)
            },
            onOpenSettings = { screen = if (current.helperPinHash == null) Screen.Settings else Screen.Unlock },
        )
        Screen.Reminder -> {
            val reminder = everyone.firstOrNull { it.id == openReminderId }
            if (reminder == null) {
                LaunchedEffect(Unit) { screen = Screen.Main }
                return
            }
            val todayOutcome = history.current(reminder.id, now, current.keepReminding)?.outcome
            ReminderScreen(
                reminder = reminder,
                today = todayOutcome,
                canDone = todayOutcome == Outcome.Due || todayOutcome == Outcome.Missed,
                onPlayAgain = { viewModel.hear(reminder) },
                onDone = {
                    viewModel.markDone(reminder)
                    screen = Screen.Main
                },
                onBack = {
                    viewModel.stopHearing()
                    screen = Screen.Main
                },
            )
        }
        Screen.Unlock -> UnlockPinScreen(
            helperPin = RemindersHelperPin,
            pinHash = current.helperPinHash ?: "",
            onUnlocked = { screen = Screen.Settings },
            onCancel = { screen = Screen.Main },
        )
        Screen.Settings -> SettingsScreen(
            settings = current,
            reminders = everyone,
            history = history,
            voice = voice,
            voiceNeeded = voiceNeeded,
            problems = problems,
            onUpdate = viewModel::update,
            onFix = fixProblem,
            onEditPlace = { place -> editReminder(place, Screen.Settings) },
            onTestVoice = viewModel::testVoice,
            onSetPin = { pinReturn = Screen.Settings; screen = Screen.SetPin },
            onRemovePin = removePin,
            onStartSetup = { setupStep = 0; screen = Screen.Setup },
            onBack = { screen = Screen.Main },
        )
        Screen.Setup -> SetupScreen(
            step = setupStep,
            onStep = { setupStep = it },
            settings = current,
            reminders = everyone,
            problems = setupProblems,
            onUpdate = viewModel::update,
            onEditPlace = { place -> editReminder(place, Screen.Setup) },
            onSetPin = { pinReturn = Screen.Setup; screen = Screen.SetPin },
            onRemovePin = removePin,
            onFix = fixProblem,
            onFinish = finishSetup,
        )
        Screen.SetPin -> SetPinScreen(
            onPinSet = { pin ->
                viewModel.update { it.copy(helperPinHash = RemindersHelperPin.hash(pin)) }
                screen = pinReturn
            },
            onCancel = { screen = pinReturn },
        )
        Screen.EditReminder -> EditorScreen(
            reminders = everyone,
            place = editPlace,
            remindersOnScreen = current.remindersOnScreen,
            onSave = saveReminder,
            onRemove = { reminder ->
                viewModel.removeReminder(reminder)
                screen = editorReturn
            },
            onBack = { screen = editorReturn },
        )
    }
}
