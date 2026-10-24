package io.github.nikoapps.reminders.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.getSystemService
import io.github.nikoapps.commons.speech.Speaker
import io.github.nikoapps.commons.speech.VoiceStatus
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/**
 * Plays one reminder's message: the family's recording through Reminders' own [MediaPlayer], or
 * the phone's voice saying a sentence when there's no recording. [usage] is the audio stream:
 * the alarm stream when a reminder comes due, so it plays on silent with its own volume, and the
 * accessibility stream when someone taps to hear it again.
 *
 * Create and use it on the main thread. Call [release] when done.
 */
class ReminderPlayer(context: Context, private val usage: Int) {
    private val app = context.applicationContext
    private val audio = app.getSystemService<AudioManager>()
    private var player: MediaPlayer? = null

    /** How a recording ended. A recording stuck past the time limit counts as played: it did start. */
    private enum class FileResult { Played, Failed, Stopped }

    /** Waiting for the recording to end, so [stop] can end the wait at once. A released player never says it ended. */
    private var pending: CancellableContinuation<FileResult>? = null

    /** [stop] was called during the current [play]. */
    @Volatile
    private var stopped = false

    /** Inside [play], whose own start clears [stopped]. */
    private var playing = false
    private val spoken = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private val voiceReady = CompletableDeferred<Boolean>()
    // Started only when a sentence is first needed, since most reminders have a recording.
    private var speaker: Speaker? = null

    private fun speaker(): Speaker = speaker ?: Speaker(
        context = app,
        audioUsage = usage,
        onReady = { voiceReady.complete(it) },
        onDone = { id -> spoken.remove(id)?.complete(Unit) },
    ).also { speaker = it }

    private val attributes = AudioAttributes.Builder()
        .setUsage(usage)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    /**
     * Plays [recording] if there is one and it can be played, otherwise says [sentence] in
     * [locale] at [rate]. [volume] runs from 0 to 1 of the stream's volume. Returns once it has
     * finished, or after a time limit if the player or voice gets stuck.
     */
    suspend fun play(recording: File?, sentence: String?, locale: Locale?, rate: Float, volume: Float, vibrate: Boolean) {
        if (vibrate) vibrate()
        val focus = requestFocus()
        try {
            stopped = false
            playing = true
            val result = recording?.takeIf { it.isFile }?.let { playFile(it, volume) } ?: FileResult.Failed
            // The voice only stands in for a recording that couldn't play, never one that was stopped.
            if (result == FileResult.Failed && !stopped && sentence != null && locale != null) say(sentence, locale, rate, volume)
        } finally {
            playing = false
            focus?.let { audio?.abandonAudioFocusRequest(it) }
        }
    }

    /** Says [sentence] in the phone's voice, such as "Done." */
    suspend fun say(sentence: String, locale: Locale, rate: Float, volume: Float = 1f) {
        if (!playing) stopped = false
        val speaker = speaker()
        val ready = withTimeoutOrNull(VOICE_START_MS) { voiceReady.await() } ?: false
        if (!ready) return
        // No installed voice for the language: say nothing rather than the words in another voice.
        if (!useVoice(speaker, locale)) return
        speaker.setRate(rate)
        val id = UUID.randomUUID().toString()
        val done = CompletableDeferred<Unit>().also { spoken[id] = it }
        if (!speaker.speak(sentence, volume = volume, utteranceId = id)) {
            spoken.remove(id)
            return
        }
        withTimeoutOrNull(SPEAK_LIMIT_MS) { done.await() }
    }

    /**
     * Whether the phone's voice can say a sentence in [locale]: starts the speech engine if it
     * isn't running yet. Null [locale] means Reminders has no sentences for the language.
     */
    suspend fun voiceStatus(locale: Locale?): VoiceStatus {
        if (locale == null) return VoiceStatus.LanguageNotSupported
        val speaker = speaker()
        val ready = withTimeoutOrNull(VOICE_START_MS) { voiceReady.await() } ?: return VoiceStatus.Starting
        if (!ready) return VoiceStatus.NoEngine
        return if (speaker.useVoiceFor(locale)) VoiceStatus.Ready else VoiceStatus.NoVoice
    }

    /** The speech engine's name as the phone shows it, once it has started. */
    fun engineName(): String? = speaker?.engineName()

    /**
     * Right after a restart the speech engine can list its voices as not installed for a few
     * seconds (Hub ADR 0036), so it's asked again for up to [VOICE_WAIT_MS], as Niko Clock does.
     */
    private suspend fun useVoice(speaker: Speaker, locale: Locale): Boolean {
        val until = System.currentTimeMillis() + VOICE_WAIT_MS
        while (true) {
            if (speaker.useVoiceFor(locale)) return true
            if (stopped || System.currentTimeMillis() >= until) return false
            delay(VOICE_RETRY_MS)
        }
    }

    /** Stops whatever is playing or being said. A reminder stopped part way doesn't go on to the voice. */
    fun stop() {
        stopped = true
        pending?.let { if (it.isActive) it.resume(FileResult.Stopped) }
        pending = null
        release(player)
        player = null
        speaker?.stop()
    }

    private fun release(media: MediaPlayer?) {
        media?.let {
            runCatching { it.stop() }
            it.release()
        }
    }

    fun release() {
        stop()
        speaker?.shutdown()
        speaker = null
    }

    private suspend fun playFile(file: File, volume: Float): FileResult {
        release(player)
        val next = MediaPlayer()
        player = next
        val prepared = withContext(Dispatchers.IO) {
            runCatching {
                next.setAudioAttributes(attributes)
                next.setDataSource(file.path)
                next.setVolume(volume, volume)
                next.prepare()
            }.isSuccess
        }
        if (player !== next) return FileResult.Stopped
        if (!prepared) {
            release(next)
            player = null
            return FileResult.Failed
        }
        val result = withTimeoutOrNull(RECORDING_LIMIT_MS) {
            suspendCancellableCoroutine { continuation ->
                pending = continuation
                next.setOnCompletionListener { if (continuation.isActive) continuation.resume(FileResult.Played) }
                next.setOnErrorListener { _, _, _ ->
                    if (continuation.isActive) continuation.resume(FileResult.Failed)
                    true
                }
                continuation.invokeOnCancellation { if (player === next) stop() }
                runCatching { next.start() }.onFailure { if (continuation.isActive) continuation.resume(FileResult.Failed) }
            }
        }
        pending = null
        if (player === next) {
            release(next)
            player = null
        }
        return result ?: FileResult.Played
    }

    private fun requestFocus(): AudioFocusRequest? {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .build()
        // Plays even if focus is refused: a reminder must be heard.
        audio?.requestAudioFocus(request)
        return request
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            app.getSystemService<VibratorManager>()?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            app.getSystemService<Vibrator>()
        } ?: return
        if (!vibrator.hasVibrator()) return
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 600, 300, 600, 300, 600), -1)
        vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
    }

    private companion object {
        /** A cold speech engine can take a few seconds to start. */
        const val VOICE_START_MS = 8_000L
        const val SPEAK_LIMIT_MS = 20_000L
        const val VOICE_WAIT_MS = 10_000L
        const val VOICE_RETRY_MS = 1_000L

        /** Recordings are at most 15 seconds; this leaves room for a slow start. */
        const val RECORDING_LIMIT_MS = 30_000L
    }
}
