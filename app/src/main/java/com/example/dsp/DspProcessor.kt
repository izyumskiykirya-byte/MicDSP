package com.example.dsp

import com.example.model.AudioStats
import com.example.model.DspSettings
import kotlin.math.*

/**
 * High-performance, zero-allocation real-time DSP audio processing engine
 * for in-vehicle microphones and automotive head units.
 */
class DspProcessor(
    private var sampleRate: Int = 48000
) {
    // Reusable float buffers to avoid GC pauses during real-time streaming
    private var floatBuffer = FloatArray(4096)

    // Biquad filter state for High-Pass Filter (Low Cut)
    private val hpf = BiquadFilter()

    // 5 Biquad filters for 5-band EQ
    private val eqBands = Array(5) { BiquadFilter() }
    private val eqFrequencies = floatArrayOf(80f, 250f, 1000f, 3500f, 8000f)
    private val eqQ = floatArrayOf(0.707f, 1.2f, 1.0f, 1.2f, 0.707f)

    // Noise Gate state
    private var gateEnvelope = 0.0f
    private var gateGain = 0.0f
    private var isGateOpenState = false

    // Dynamic Noise Reduction (Software Fallback) state
    private var noiseFloorEstimate = 0.005f // ~ -46 dBFS initial noise floor estimate
    private val noiseAlpha = 0.0005f // slow tracking for stationary cabin noise

    // Telemetry & metrics
    private var lastInputRmsDb = -100f
    private var lastInputPeakDb = -100f
    private var lastOutputRmsDb = -100f
    private var lastOutputPeakDb = -100f
    private var isClippingDetected = false

    init {
        configureFilters(DspSettings())
    }

    /**
     * Updates DSP filter coefficients according to the current settings
     */
    fun configureFilters(settings: DspSettings) {
        sampleRate = settings.sampleRate

        // 1. Configure High-Pass Filter (2nd order Butterworth)
        if (settings.highPassFilterEnabled) {
            hpf.setHighPass(settings.highPassCutoffHz, sampleRate.toFloat(), 0.707f)
        } else {
            hpf.setPassThrough()
        }

        // 2. Configure 5-Band EQ
        for (i in 0 until 5) {
            val gainDb = settings.eqGainsDb.getOrElse(i) { 0f }
            val freq = eqFrequencies[i]
            val q = eqQ[i]
            when (i) {
                0 -> eqBands[i].setLowShelf(freq, sampleRate.toFloat(), gainDb, q)
                4 -> eqBands[i].setHighShelf(freq, sampleRate.toFloat(), gainDb, q)
                else -> eqBands[i].setPeakingEq(freq, sampleRate.toFloat(), gainDb, q)
            }
        }
    }

    /**
     * Processes raw 16-bit PCM audio samples in-place.
     * @param pcmBuffer ByteArray containing 16-bit Little-Endian PCM audio
     * @param bytesRead number of valid bytes in buffer
     * @param settings current DSP parameters
     * @return updated AudioStats for UI visualization
     */
    fun process(
        pcmBuffer: ByteArray,
        bytesRead: Int,
        settings: DspSettings
    ): AudioStats {
        val sampleCount = bytesRead / 2
        if (sampleCount <= 0) {
            return createStats()
        }

        // Ensure float buffer size
        if (floatBuffer.size < sampleCount) {
            floatBuffer = FloatArray(sampleCount)
        }

        // 1. Convert 16-bit PCM LE to Normalized Float [-1.0 .. 1.0] and compute Input RMS/Peak
        var inSumSquare = 0.0
        var inPeak = 0.0f
        var byteIdx = 0
        for (i in 0 until sampleCount) {
            val low = pcmBuffer[byteIdx].toInt() and 0xFF
            val high = pcmBuffer[byteIdx + 1].toInt()
            val sampleShort = (high shl 8) or low
            val normalized = (sampleShort / 32768.0f).coerceIn(-1.0f, 1.0f)
            floatBuffer[i] = normalized

            val absVal = abs(normalized)
            if (absVal > inPeak) inPeak = absVal
            inSumSquare += (normalized * normalized)
            byteIdx += 2
        }

        val inRms = sqrt(inSumSquare / sampleCount).toFloat()
        lastInputRmsDb = amplitudeToDb(inRms)
        lastInputPeakDb = amplitudeToDb(inPeak)

        // 2. Apply High-Pass Filter (Low-Cut to eliminate engine rumble)
        if (settings.highPassFilterEnabled) {
            for (i in 0 until sampleCount) {
                floatBuffer[i] = hpf.process(floatBuffer[i])
            }
        }

        // 3. Apply 5-Band Equalizer
        for (band in eqBands) {
            if (!band.isPassThrough) {
                for (i in 0 until sampleCount) {
                    floatBuffer[i] = band.process(floatBuffer[i])
                }
            }
        }

        // 4. Software Dynamic Noise Suppressor (Spectral floor subtraction fallback)
        if (settings.softwareNsEnabled) {
            val strength = settings.softwareNsStrength.coerceIn(0.0f, 1.0f)
            for (i in 0 until sampleCount) {
                val absVal = abs(floatBuffer[i])
                // Adaptively track noise floor when signal is low
                if (absVal < noiseFloorEstimate * 2.0f) {
                    noiseFloorEstimate += noiseAlpha * (absVal - noiseFloorEstimate)
                }
                // Attenuate components close to the noise floor
                val threshold = noiseFloorEstimate * (1.5f + strength * 2.0f)
                if (absVal < threshold) {
                    val attenuation = max(0.05f, 1.0f - strength * (1.0f - (absVal / threshold)))
                    floatBuffer[i] *= attenuation
                }
            }
        }

        // 5. Noise Gate (Silences cabin noise and A/C when speaker is not talking)
        val gateThresholdLinear = dbToAmplitude(settings.noiseGateThresholdDb)
        val attackCoeff = exp(-1.0f / (settings.noiseGateAttackMs * 0.001f * sampleRate))
        val releaseCoeff = exp(-1.0f / (settings.noiseGateReleaseMs * 0.001f * sampleRate))

        if (settings.noiseGateEnabled) {
            for (i in 0 until sampleCount) {
                val absVal = abs(floatBuffer[i])
                // Envelope follower
                gateEnvelope = if (absVal > gateEnvelope) {
                    attackCoeff * gateEnvelope + (1.0f - attackCoeff) * absVal
                } else {
                    releaseCoeff * gateEnvelope + (1.0f - releaseCoeff) * absVal
                }

                // Hysteresis threshold: opens at threshold, closes 3dB lower to avoid chatter
                val closeThreshold = gateThresholdLinear * 0.707f
                if (gateEnvelope > gateThresholdLinear) {
                    isGateOpenState = true
                } else if (gateEnvelope < closeThreshold) {
                    isGateOpenState = false
                }

                // Smooth gain transition
                val targetGain = if (isGateOpenState) 1.0f else 0.01f // -40dB floor when closed
                gateGain = if (targetGain > gateGain) {
                    attackCoeff * gateGain + (1.0f - attackCoeff) * targetGain
                } else {
                    releaseCoeff * gateGain + (1.0f - releaseCoeff) * targetGain
                }

                floatBuffer[i] *= gateGain
            }
        } else {
            isGateOpenState = true
            gateGain = 1.0f
        }

        // 6. Gain Booster (0 to +30 dB)
        val gainMultiplier = dbToAmplitude(settings.gainBoosterDb)
        if (settings.gainBoosterDb != 0.0f) {
            for (i in 0 until sampleCount) {
                floatBuffer[i] *= gainMultiplier
            }
        }

        // 7. Dynamic Limiter / Soft Saturation (Prevents clipping from screaming or loud bursts)
        val limiterLinear = dbToAmplitude(settings.limiterThresholdDb)
        isClippingDetected = false

        if (settings.limiterEnabled) {
            for (i in 0 until sampleCount) {
                var s = floatBuffer[i]
                val absS = abs(s)
                if (absS > limiterLinear) {
                    // Soft hyperbolic tangent knee above limiter threshold
                    val excess = absS - limiterLinear
                    val compressedExcess = tanh(excess * 1.5f) * (1.0f - limiterLinear)
                    val limitedMagnitude = (limiterLinear + compressedExcess).coerceAtMost(0.999f)
                    s = if (s >= 0) limitedMagnitude else -limitedMagnitude
                    floatBuffer[i] = s
                }
            }
        }

        // 8. Convert back to 16-bit PCM LE and compute Output RMS/Peak
        var outSumSquare = 0.0
        var outPeak = 0.0f
        byteIdx = 0
        for (i in 0 until sampleCount) {
            var sample = floatBuffer[i]
            val absSample = abs(sample)
            if (absSample > outPeak) outPeak = absSample
            outSumSquare += (sample * sample)

            if (absSample >= 0.995f) {
                isClippingDetected = true
            }

            // Hard clamp just in case before quantization
            sample = sample.coerceIn(-1.0f, 1.0f)
            val pcm16 = (sample * 32767.0f).roundToInt()
            pcmBuffer[byteIdx] = (pcm16 and 0xFF).toByte()
            pcmBuffer[byteIdx + 1] = ((pcm16 shr 8) and 0xFF).toByte()
            byteIdx += 2
        }

        val outRms = sqrt(outSumSquare / sampleCount).toFloat()
        lastOutputRmsDb = amplitudeToDb(outRms)
        lastOutputPeakDb = amplitudeToDb(outPeak)

        return createStats()
    }

    private fun createStats(): AudioStats {
        return AudioStats(
            inputRmsDb = lastInputRmsDb,
            inputPeakDb = lastInputPeakDb,
            outputRmsDb = lastOutputRmsDb,
            outputPeakDb = lastOutputPeakDb,
            isGateOpen = isGateOpenState,
            isClipping = isClippingDetected
        )
    }

    private fun amplitudeToDb(amp: Float): Float {
        return if (amp > 0.00001f) (20.0f * log10(amp)).coerceIn(-100.0f, 0.0f) else -100.0f
    }

    private fun dbToAmplitude(db: Float): Float {
        return 10.0f.pow(db / 20.0f)
    }

    /**
     * Standard Biquad IIR Filter (Transposed Direct Form II)
     * Derived from Robert Bristow-Johnson Audio EQ Cookbook formulas.
     */
    class BiquadFilter {
        private var b0 = 1.0f
        private var b1 = 0.0f
        private var b2 = 0.0f
        private var a1 = 0.0f
        private var a2 = 0.0f

        // Delay state
        private var z1 = 0.0f
        private var z2 = 0.0f

        var isPassThrough = false
            private set

        fun setPassThrough() {
            b0 = 1.0f
            b1 = 0.0f
            b2 = 0.0f
            a1 = 0.0f
            a2 = 0.0f
            isPassThrough = true
        }

        fun process(input: Float): Float {
            if (isPassThrough) return input
            val out = b0 * input + z1
            z1 = b1 * input - a1 * out + z2
            z2 = b2 * input - a2 * out
            return out
        }

        fun setHighPass(fc: Float, fs: Float, q: Float = 0.707f) {
            isPassThrough = false
            val w0 = (2.0 * PI * fc / fs).toFloat()
            val cosW0 = cos(w0)
            val alpha = (sin(w0) / (2.0f * q)).toFloat()

            val a0 = 1.0f + alpha
            b0 = ((1.0f + cosW0) / 2.0f) / a0
            b1 = (-(1.0f + cosW0)) / a0
            b2 = ((1.0f + cosW0) / 2.0f) / a0
            a1 = (-2.0f * cosW0) / a0
            a2 = (1.0f - alpha) / a0
        }

        fun setPeakingEq(fc: Float, fs: Float, gainDb: Float, q: Float = 1.0f) {
            if (abs(gainDb) < 0.05f) {
                setPassThrough()
                return
            }
            isPassThrough = false
            val a = 10.0f.pow(gainDb / 40.0f)
            val w0 = (2.0 * PI * fc / fs).toFloat()
            val cosW0 = cos(w0)
            val alpha = (sin(w0) / (2.0f * q)).toFloat()

            val a0 = 1.0f + alpha / a
            b0 = (1.0f + alpha * a) / a0
            b1 = (-2.0f * cosW0) / a0
            b2 = (1.0f - alpha * a) / a0
            a1 = (-2.0f * cosW0) / a0
            a2 = (1.0f - alpha / a) / a0
        }

        fun setLowShelf(fc: Float, fs: Float, gainDb: Float, q: Float = 0.707f) {
            if (abs(gainDb) < 0.05f) {
                setPassThrough()
                return
            }
            isPassThrough = false
            val a = 10.0f.pow(gainDb / 40.0f)
            val w0 = (2.0 * PI * fc / fs).toFloat()
            val cosW0 = cos(w0)
            val sinW0 = sin(w0)
            val alpha = (sinW0 / (2.0f * q)).toFloat()
            val sqrtA2 = 2.0f * sqrt(a) * alpha

            val a0 = (a + 1.0f) + (a - 1.0f) * cosW0 + sqrtA2
            b0 = (a * ((a + 1.0f) - (a - 1.0f) * cosW0 + sqrtA2)) / a0
            b1 = (2.0f * a * ((a - 1.0f) - (a + 1.0f) * cosW0)) / a0
            b2 = (a * ((a + 1.0f) - (a - 1.0f) * cosW0 - sqrtA2)) / a0
            a1 = (-2.0f * ((a - 1.0f) + (a + 1.0f) * cosW0)) / a0
            a2 = ((a + 1.0f) + (a - 1.0f) * cosW0 - sqrtA2) / a0
        }

        fun setHighShelf(fc: Float, fs: Float, gainDb: Float, q: Float = 0.707f) {
            if (abs(gainDb) < 0.05f) {
                setPassThrough()
                return
            }
            isPassThrough = false
            val a = 10.0f.pow(gainDb / 40.0f)
            val w0 = (2.0 * PI * fc / fs).toFloat()
            val cosW0 = cos(w0)
            val sinW0 = sin(w0)
            val alpha = (sinW0 / (2.0f * q)).toFloat()
            val sqrtA2 = 2.0f * sqrt(a) * alpha

            val a0 = (a + 1.0f) - (a - 1.0f) * cosW0 + sqrtA2
            b0 = (a * ((a + 1.0f) + (a - 1.0f) * cosW0 + sqrtA2)) / a0
            b1 = (-2.0f * a * ((a - 1.0f) + (a + 1.0f) * cosW0)) / a0
            b2 = (a * ((a + 1.0f) + (a - 1.0f) * cosW0 - sqrtA2)) / a0
            a1 = (2.0f * ((a - 1.0f) - (a + 1.0f) * cosW0)) / a0
            a2 = ((a + 1.0f) - (a - 1.0f) * cosW0 - sqrtA2) / a0
        }
    }
}
