package com.prayerwheel.app.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Microphone-amplitude driver for Breath/Wind spin mode.
 *
 * Reads mono 16-bit PCM from the built-in microphone at 8 kHz — low, because
 * only the RMS amplitude envelope matters, not audio quality — and exposes a
 * normalized 0..1 amplitude as a [StateFlow]. The wheel physics loop reads
 * [amplitude] each frame and applies torque proportional to it.
 *
 * PRIVACY CONTRACT: no audio is ever recorded to a file, encoded, persisted,
 * or transmitted. Each PCM frame exists only in a transient in-memory buffer
 * long enough to compute its RMS, then is immediately overwritten by the next
 * read. There is no file output, no network output, and no on-disk caching of
 * any kind. The microphone is released the moment [stop] is called.
 *
 * PERMISSIONS: if RECORD_AUDIO is not granted, [start] is a graceful no-op —
 * [amplitude] stays at 0 and the wheel simply does not respond to breath.
 *
 * Thread safety: [start] and [stop] may be called from the main thread. The
 * AudioRecord lifecycle runs on [Dispatchers.IO]. [audioRecord] is `@Volatile`
 * so the IO thread and any reader of [amplitude] see a consistent state.
 */
class BreathModeController(
    private val appContext: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val SAMPLE_RATE_HZ = 8000
        private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val POLL_INTERVAL_MS = 50L

        // 16-bit signed PCM peak magnitude is 32767. Empirically calibrated so
        // gentle breath registers ≈ 0.2–0.4, steady breath ≈ 0.4–0.7, and a
        // strong blow into the microphone saturates near 1.0.
        private const val RMS_CALIBRATION = 4000f

        // Normalized amplitudes below this are treated as silence to suppress
        // room tone, HVAC, keyboard clatter, and other ambient contamination.
        private const val NOISE_GATE = 0.05f
    }

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private var recordJob: Job? = null

    // Held in a @Volatile field so stop() (called from any thread) can release
    // the AudioRecord that the IO coroutine created, and so onCleared() can
    // guarantee the microphone is freed even if the coroutine is mid-read.
    @Volatile
    private var audioRecord: AudioRecord? = null

    /**
     * Begins polling microphone amplitude on a background thread. Idempotent —
     * calling while already running is a no-op. Safe to call when RECORD_AUDIO
     * is not granted; in that case this returns without launching a coroutine.
     */
    fun start() {
        if (recordJob?.isActive == true) return

        recordJob = scope.launch(Dispatchers.IO) {
            val granted = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return@launch

            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE_HZ,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )
            if (minBuf <= 0) return@launch

            val buffer = ShortArray(minBuf)

            val record = try {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE_HZ,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    minBuf * 2 // 2x the minimum for headroom against underruns
                )
            } catch (e: IllegalArgumentException) {
                return@launch
            } catch (e: SecurityException) {
                // Permission was revoked between check and construct — degrade gracefully.
                return@launch
            } catch (e: IllegalStateException) {
                // OEM edge case: mic is already in use — degrade gracefully.
                return@launch
            }

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                return@launch
            }

            audioRecord = record
            try {
                record.startRecording()
            } catch (e: IllegalStateException) {
                record.release()
                audioRecord = null
                return@launch
            }

            try {
                while (isActive) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        var sumSquared = 0L
                        for (i in 0 until read) {
                            val sample = buffer[i].toInt()
                            sumSquared += sample.toLong() * sample.toLong()
                        }
                        val rms = sqrt(sumSquared.toDouble() / read).toFloat()
                        val normalized = (rms / RMS_CALIBRATION).coerceIn(0f, 1f)
                        _amplitude.value = if (normalized < NOISE_GATE) 0f else normalized
                    }
                    delay(POLL_INTERVAL_MS)
                }
            } finally {
                try { record.stop() } catch (_: IllegalStateException) {}
                record.release()
                audioRecord = null
                _amplitude.value = 0f
            }
        }
    }

    /**
     * Stops polling and releases the microphone immediately. Safe to call
     * from any thread; idempotent.
     */
    fun stop() {
        recordJob?.cancel()
        recordJob = null
        audioRecord?.let { rec ->
            try { rec.stop() } catch (_: IllegalStateException) {}
            rec.release()
        }
        audioRecord = null
        _amplitude.value = 0f
    }
}
