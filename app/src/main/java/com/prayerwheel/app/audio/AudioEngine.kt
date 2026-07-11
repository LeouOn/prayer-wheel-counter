package com.prayerwheel.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Procedural audio engine for the prayer wheel.
 *
 * All sound is synthesized at runtime — no audio assets exist in the project.
 * Two independent voices:
 *
 *  1. **Singing bowl** — struck tones at lifetime-mantra milestones. Each tone is
 *     rendered on a background thread as a 4-second buffer of 4 detuned sine
 *     partials shaped by a strike envelope (fast attack, exponential decay),
 *     then played once via an [AudioTrack] in MODE_STATIC. Lower milestones
 *     (1K) sound deeper; higher milestones (1T) are brighter.
 *
 *  2. **Ambient drone** — a continuous low-frequency bed (65 Hz + 130 Hz) that
 *     fades in as RPM rises from 0 to 60, then plateaus. Runs on a long-lived
 *     coroutine writing to an [AudioTrack] in MODE_STREAM. The track stays
 *     alive while the wheel screen is active; when RPM is 0 (or ambient is
 *     disabled) it plays silence so that motion resumes without any audible
 *     "kick-in" artifact.
 *
 * Thread safety: every public entry point is safe to call from the main thread.
 * All AudioTrack operations are dispatched onto background threads/coroutines.
 * Mutable settings are `@Volatile` for cross-thread visibility.
 */
class AudioEngine {

    companion object {
        private const val SAMPLE_RATE = 44100

        // ── Singing bowl ────────────────────────────────────────────────────
        /** Total rendered bowl duration (strike + full exponential tail). */
        private const val BOWL_DURATION_SECONDS = 4.0
        /** Strike attack — the felt-mallet "tap" before the body blooms. */
        private const val BOWL_ATTACK_SECONDS = 0.02
        /** Bowl decay time constant in seconds. exp(-4/0.8) ≈ 0.0067 at tail. */
        private const val BOWL_DECAY_TAU_SECONDS = 0.8
        /** Bowl peak gain as a fraction of master volume. */
        private const val BOWL_MAX_GAIN_RATIO = 0.40f
        /** Small release pad after the last sample before stopping the track. */
        private const val BOWL_RELEASE_PAD_MS = 200L

        /**
         * Fundamental frequencies for the 8 milestone tiers (1K → 1T).
         * Forms a diatonic scale from G3 (deepest) to G4 (brightest).
         */
        private val BOWL_FUNDAMENTALS_HZ = doubleArrayOf(
            196.00, // tier 0 — 1K      (G3)
            220.00, // tier 1 — 10K     (A3)
            246.94, // tier 2 — 100K    (B3)
            261.63, // tier 3 — 1M      (C4)
            293.66, // tier 4 — 10M     (D4)
            329.63, // tier 5 — 100M    (E4)
            349.23, // tier 6 — 1B      (F4)
            392.00  // tier 7 — 1T      (G4)
        )

        /** Musical intervals of the four partials above the fundamental. */
        private val BOWL_PARTIAL_RATIOS = doubleArrayOf(1.0, 1.5, 2.0, 3.0)
        /** Relative amplitudes — fundamental dominant, harmonics tapering. */
        private val BOWL_PARTIAL_AMPS = doubleArrayOf(1.00, 0.55, 0.32, 0.18)
        /** Per-partial detune in Hz — produces the slow beating of a real bowl. */
        private val BOWL_DETUNE_HZ = doubleArrayOf(0.0, 0.4, -0.3, 0.6)
        /** Sum of [BOWL_PARTIAL_AMPS] — used to normalize the partial sum to ≤ 1.0. */
        private const val BOWL_PARTIAL_AMP_SUM = 1.0 + 0.55 + 0.32 + 0.18 // 2.05

        // ── Ambient drone ───────────────────────────────────────────────────
        /** Fundamental and octave-harmonic frequencies of the drone. */
        private const val DRONE_FREQ_FUNDAMENTAL_HZ = 65.0
        private const val DRONE_FREQ_HARMONIC_HZ = 130.0
        /** Harmonic amplitude as a fraction of the fundamental. */
        private const val DRONE_HARMONIC_AMP = 0.5
        /** Drone peak gain as a fraction of master volume (at ≥ 60 RPM). */
        private const val DRONE_MAX_GAIN_RATIO = 0.15f
        /** RPM at which the drone reaches full intensity. */
        private const val DRONE_FULL_INTENSITY_RPM = 60f
        /** Per-iteration render chunk (≈ 232 ms at 44.1 kHz). */
        private const val DRONE_CHUNK_FRAMES = 10240
    }

    // ── Mutable settings (written from main thread, read from audio threads) ──
    @Volatile private var masterVolume: Float = 1.0f
    @Volatile private var bellEnabled: Boolean = false
    @Volatile private var ambientEnabled: Boolean = false

    /** Smoothed 0..1 intensity derived from RPM by [updateDrone]. */
    @Volatile private var droneIntensity: Float = 0f

    // ── Drone coroutine state ────────────────────────────────────────────────
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var droneJob: Job? = null
    @Volatile private var droneTrack: AudioTrack? = null
    @Volatile private var droneAlive: Boolean = false

    /**
     * Updates the gating settings. Safe to call from the main thread.
     * When [bellEnabled] is false, no bowl tones will play. When [ambientEnabled]
     * is false, the drone (if running) writes silence but its track stays alive.
     */
    fun updateSettings(masterVolume: Float, bellEnabled: Boolean, ambientEnabled: Boolean) {
        this.masterVolume = masterVolume.coerceIn(0f, 1f)
        this.bellEnabled = bellEnabled
        this.ambientEnabled = ambientEnabled
    }

    /**
     * Sets the current RPM so the drone can fade in/out smoothly.
     * Maps [0, 60] RPM → [0, 1] intensity via a smoothstep, then plateaus.
     */
    fun updateDrone(rpm: Float) {
        val n = (rpm / DRONE_FULL_INTENSITY_RPM).coerceIn(0f, 1f)
        // Smoothstep: 3n² − 2n³ — gentle S-curve, no audible "kick-in".
        droneIntensity = n * n * (3f - 2f * n)
    }

    /**
     * Synthesizes and plays a singing bowl strike for the given milestone tier.
     * No-op when bells are disabled or master volume is 0. Returns immediately;
     * render + playback happen on a background [Thread] so the caller (often the
     * physics loop) is never blocked.
     *
     * @param tier 0 (1K, deepest) through 7 (1T, brightest). Out-of-range values
     *             are clamped.
     */
    fun playBowlTone(tier: Int) {
        if (!bellEnabled || masterVolume <= 0f) return
        val clampedTier = tier.coerceIn(0, BOWL_FUNDAMENTALS_HZ.size - 1)
        // Each bowl gets its own short-lived thread — render is CPU work that
        // must not block the main thread nor the drone coroutine.
        Thread({ playBowlInternal(clampedTier) }, "bowl-tone-$clampedTier").start()
    }

    private fun playBowlInternal(tier: Int) {
        val buffer = renderBowl(tier)
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(buffer.size * 2) // 16-bit samples → 2 bytes/frame
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        } catch (e: Exception) {
            return // AudioTrack construction can fail on unusual hardware — audio is non-critical.
        }

        // In MODE_STATIC the buffer is loaded before play(); write returns the
        // frame count transferred to the audio sink.
        track.write(buffer, 0, buffer.size)
        try {
            track.play()
        } catch (e: IllegalStateException) {
            track.release()
            return
        }

        // Block this (already background) thread until playback finishes, then
        // release. Simpler and more reliable than the position-marker listener,
        // which can race on some OEMs.
        val playMs = (BOWL_DURATION_SECONDS * 1000.0).toLong() + BOWL_RELEASE_PAD_MS
        try {
            Thread.sleep(playMs)
        } catch (e: InterruptedException) {
            // Treat interruption as "stop early" — fall through to release.
        }
        runCatching {
            track.stop()
            track.release()
        }
    }

    /**
     * Renders a single singing bowl strike as 16-bit PCM.
     *
     * Synthesis per sample:
     * ```
     * sample = Σₚ sin(2π·fₚ·t/sr) · env(t) · ampₚ
     * ```
     * where `fₚ = fundamental·ratioₚ + detuneₚ` and `env` is the strike envelope
     * (20 ms linear attack → exp(−t/τ) decay). The partial sum is normalized by
     * the sum of partial amplitudes, then scaled by `[BOWL_MAX_GAIN_RATIO]·masterVolume`.
     */
    private fun renderBowl(tier: Int): ShortArray {
        val totalSamples = (SAMPLE_RATE * BOWL_DURATION_SECONDS).toInt()
        val attackSamples = (SAMPLE_RATE * BOWL_ATTACK_SECONDS).toInt()
        val tauSamples = BOWL_DECAY_TAU_SECONDS * SAMPLE_RATE

        val fundamental = BOWL_FUNDAMENTALS_HZ[tier]
        val partialsFreq = DoubleArray(BOWL_PARTIAL_RATIOS.size) { i ->
            fundamental * BOWL_PARTIAL_RATIOS[i] + BOWL_DETUNE_HZ[i]
        }
        val peakGain = BOWL_MAX_GAIN_RATIO * masterVolume
        val twoPiOverSr = 2.0 * PI / SAMPLE_RATE

        val floatOut = DoubleArray(totalSamples)
        var peak = 0.0
        for (i in 0 until totalSamples) {
            val t = i.toDouble()
            // Strike envelope — linear attack (mallet tap) then exponential body decay.
            val env = if (i < attackSamples) {
                i.toDouble() / attackSamples.toDouble()
            } else {
                exp(-(i - attackSamples).toDouble() / tauSamples)
            }

            var sample = 0.0
            for (p in partialsFreq.indices) {
                sample += sin(twoPiOverSr * partialsFreq[p] * t) * env * BOWL_PARTIAL_AMPS[p]
            }
            // Normalize so the partial stack can't clip before master gain.
            sample = sample / BOWL_PARTIAL_AMP_SUM * peakGain
            floatOut[i] = sample
            val mag = if (sample < 0.0) -sample else sample
            if (mag > peak) peak = mag
        }

        // Safety soft-limit — if peak > 1 (shouldn't happen with the math above),
        // renormalize so the int cast never wraps around.
        val norm = if (peak > 1.0) 1.0 / peak else 1.0
        val out = ShortArray(totalSamples)
        for (i in 0 until totalSamples) {
            val scaled = (floatOut[i] * norm * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            out[i] = scaled.toShort()
        }
        return out
    }

    /**
     * Starts the long-lived ambient drone coroutine. Idempotent — repeated calls
     * are no-ops while the drone is already running. Returns immediately; the
     * loop runs on [Dispatchers.Default] until [stopDroneLoop] is called.
     */
    fun startDroneLoop() {
        if (droneAlive) return
        droneAlive = true
        droneJob = engineScope.launch { droneLoop() }
    }

    private suspend fun droneLoop() {
        val minBufBytes = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                // Room for ~2 chunks so AudioTrack doesn't underrun while we render.
                .setBufferSizeInBytes((minBufBytes).coerceAtLeast(DRONE_CHUNK_FRAMES * 2 * 2))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: Exception) {
            droneAlive = false
            return
        }
        droneTrack = track

        try {
            track.play()
        } catch (e: IllegalStateException) {
            runCatching { track.release() }
            droneTrack = null
            droneAlive = false
            return
        }

        val chunk = ShortArray(DRONE_CHUNK_FRAMES)
        val twoPiOverSr = 2.0 * PI / SAMPLE_RATE
        // Phase accumulators (kept across chunks for sample-accurate continuity).
        var phaseFund = 0.0
        var phaseHarm = 0.0
        val phaseStepFund = twoPiOverSr * DRONE_FREQ_FUNDAMENTAL_HZ
        val phaseStepHarm = twoPiOverSr * DRONE_FREQ_HARMONIC_HZ

        try {
            while (coroutineContext.isActive && droneAlive) {
                // Snapshot @Volatile settings once per chunk so the inner sample
                // loop sees a consistent gain; ambientEnabled gates actual output.
                val ambientOn = ambientEnabled
                val effectiveGain: Double = if (ambientOn && masterVolume > 0f) {
                    (DRONE_MAX_GAIN_RATIO * masterVolume * droneIntensity).toDouble()
                } else {
                    0.0
                }
                val amplitudeScalar = effectiveGain * Short.MAX_VALUE

                for (i in 0 until DRONE_CHUNK_FRAMES) {
                    // Drone = fundamental + half-amplitude octave harmonic.
                    val s = (sin(phaseFund) + DRONE_HARMONIC_AMP * sin(phaseHarm)) /
                        (1.0 + DRONE_HARMONIC_AMP)
                    val v = (s * amplitudeScalar).toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    chunk[i] = v.toShort()
                    phaseFund += phaseStepFund
                    phaseHarm += phaseStepHarm
                    if (phaseFund >= 2.0 * PI) phaseFund -= 2.0 * PI
                    if (phaseHarm >= 2.0 * PI) phaseHarm -= 2.0 * PI
                }

                // Blocking write — back-pressures the loop to real-time pace.
                track.write(chunk, 0, DRONE_CHUNK_FRAMES, AudioTrack.WRITE_BLOCKING)
            }
        } finally {
            // Guard against the double-release race: stopDroneLoop() may have
            // already released (and nulled) droneTrack while cancellation was
            // propagating. Only release here if we still own the same instance.
            if (droneTrack === track) {
                runCatching {
                    track.stop()
                    track.release()
                }
                droneTrack = null
            }
        }
    }

    /**
     * Stops the ambient drone coroutine and releases its AudioTrack.
     * Safe to call from the main thread (e.g. from `ViewModel.onCleared`).
     */
    fun stopDroneLoop() {
        droneAlive = false
        droneJob?.cancel()
        droneJob = null
        droneTrack?.let { t ->
            runCatching {
                t.stop()
                t.release()
            }
        }
        droneTrack = null
    }

    /**
     * Releases the engine: stops the drone loop and cancels [engineScope] so its
     * SupervisorJob can no longer leak coroutines after the owning ViewModel is
     * cleared. Safe to call from the main thread. Idempotent.
     */
    fun release() {
        stopDroneLoop()
        engineScope.cancel()
    }
}
