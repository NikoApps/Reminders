package io.github.nikoapps.reminders.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.nikoapps.commons.health.PhoneHealth
import io.github.nikoapps.commons.recording.RecordResult
import io.github.nikoapps.commons.recording.VoiceRecorder
import io.github.nikoapps.commons.ui.SecondaryButton
import io.github.nikoapps.commons.ui.SolidButton
import io.github.nikoapps.commons.ui.openPage
import io.github.nikoapps.reminders.R
import io.github.nikoapps.reminders.reminders.ReminderFiles
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/** One tap starts, one tap stops, and it stops by itself after this long (spec decision 12). */
const val MAX_MESSAGE_MS = 15_000

/** What the message section is doing right now. Not kept across a rotation: a recording in progress is dropped. */
private enum class ClipState { Idle, Recording, Playing }

/**
 * "Message" in the reminder editor: one tap on Record starts, one on Stop ends, and it ends by
 * itself after 15 seconds. Then Play, Record again and Delete. Nothing is kept until Save:
 * [onRecorded] gets a new recording waiting in the cache, [onDelete] marks the old one to go.
 *
 * [clip] is the recording to play: the new one, or the saved one, or null for none. The first
 * Record asks for the microphone, explained in one sentence above the button.
 */
@Composable
fun MessageSection(
    clip: File?,
    isNew: Boolean,
    removed: Boolean,
    onRecorded: (File) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val recorder = remember { VoiceRecorder(maxMs = MAX_MESSAGE_MS) }
    val player = remember { ClipPlayer() }
    var state by remember { mutableStateOf(ClipState.Idle) }
    var job by remember { mutableStateOf<Job?>(null) }
    var message by rememberSaveable { mutableStateOf<Int?>(null) }
    var micBlocked by rememberSaveable { mutableStateOf(false) }
    val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    // Leaving the screen ends recording and playing, and lets go of the microphone and the player.
    DisposableEffect(Unit) {
        onDispose {
            job?.cancel()
            player.release()
        }
    }
    // The phone gives no sound to an app in the background, so a recording stops and is kept.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        recorder.stop()
        player.stop()
        if (state == ClipState.Playing) state = ClipState.Idle
    }

    val startRecording = {
        player.stop()
        message = null
        val target = ReminderFiles.newRecording(context, "new-${UUID.randomUUID()}.wav")
        state = ClipState.Recording
        recorder.reset()
        job = scope.launch {
            try {
                when (recorder.record(target)) {
                    is RecordResult.Done -> onRecorded(target)
                    RecordResult.TooShort -> message = R.string.message_too_short
                    RecordResult.Failed -> message = R.string.message_failed
                }
            } finally {
                state = ClipState.Idle
            }
        }
    }
    val askToRecord = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            micBlocked = false
            startRecording()
        } else {
            micBlocked = activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
            message = if (micBlocked) R.string.message_mic_off else R.string.message_needs_mic
        }
    }
    val record = {
        if (hasMic) startRecording() else askToRecord.launch(Manifest.permission.RECORD_AUDIO)
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = stringResource(R.string.editor_message),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        SectionText(stringResource(R.string.message_how))
        SectionText(
            text = stringResource(
                when {
                    state == ClipState.Recording -> R.string.message_now
                    removed && clip == null -> R.string.message_removed
                    clip == null -> R.string.message_none
                    isNew -> R.string.message_new
                    else -> R.string.message_ready
                },
            ),
            live = true,
        )
        message?.let { SectionText(stringResource(it), warning = true, live = true) }
        if (micBlocked) {
            SecondaryButton(text = stringResource(R.string.open_app_info), onClick = { openPage(context, PhoneHealth.appInfo(context)) })
        }
        if (state != ClipState.Recording && clip == null && !hasMic) SectionText(stringResource(R.string.message_ask_mic))

        when {
            state == ClipState.Recording -> SolidButton(
                text = stringResource(R.string.message_stop),
                onClick = recorder::stop,
                modifier = Modifier.fillMaxWidth(),
            )
            clip == null -> SolidButton(text = stringResource(R.string.message_record), onClick = record, modifier = Modifier.fillMaxWidth())
            else -> {
                if (state == ClipState.Playing) {
                    SolidButton(
                        text = stringResource(R.string.message_stop_playing),
                        onClick = {
                            player.stop()
                            state = ClipState.Idle
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    SolidButton(
                        text = stringResource(R.string.message_play),
                        onClick = {
                            message = null
                            if (player.play(clip) { state = ClipState.Idle }) state = ClipState.Playing else message = R.string.message_failed
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                SecondaryButton(text = stringResource(R.string.message_record_again), onClick = record)
                SecondaryButton(
                    text = stringResource(R.string.message_delete),
                    onClick = {
                        player.stop()
                        state = ClipState.Idle
                        message = null
                        onDelete()
                    },
                )
            }
        }
        SectionText(stringResource(R.string.message_kept))
    }
}

@Composable
private fun SectionText(text: String, warning: Boolean = false, live: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.fillMaxWidth().then(if (live) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier),
    )
}

/** Plays one recording at a time in the editor, for the helper to check. */
private class ClipPlayer {
    private var player: MediaPlayer? = null

    /** Starts [file] from the beginning. [onDone] runs when it ends by itself. False if it can't be played. */
    fun play(file: File, onDone: () -> Unit): Boolean {
        stop()
        val next = MediaPlayer()
        player = next
        return runCatching {
            next.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            next.setDataSource(file.path)
            next.setOnCompletionListener {
                stop()
                onDone()
            }
            next.prepare()
            next.start()
            true
        }.getOrElse {
            stop()
            false
        }
    }

    fun stop() {
        player?.let {
            runCatching { it.stop() }
            it.release()
        }
        player = null
    }

    fun release() = stop()
}
