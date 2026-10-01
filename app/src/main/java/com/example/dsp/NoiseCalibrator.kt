package com.example.dsp

import com.example.model.CalibrationResult
import com.example.model.DspSettings
import kotlin.math.roundToInt

/**
 * Real-time cabin noise analyzer for 10-second automotive auto-calibration.
 * Uses statistical percentile filtering to measure ambient cabin roar and road noise
 * while discarding accidental transient spikes (e.g. car horns, claps, coughs).
 */
class NoiseCalibrator {

    private val rmsSamples = mutableListOf<Float>()
    private val peakSamples = mutableListOf<Float>()

    fun reset() {
        rmsSamples.clear()
        peakSamples.clear()
    }

    fun addSample(rmsDb: Float, peakDb: Float) {
        // Discard extreme silent initialization artifacts
        if (rmsDb > -95f) {
            rmsSamples.add(rmsDb)
            peakSamples.add(peakDb)
        }
    }

    fun getSampleCount(): Int = rmsSamples.size

    fun computeResult(currentSettings: DspSettings): CalibrationResult {
        if (rmsSamples.isEmpty()) {
            return CalibrationResult(
                measuredNoiseRmsDb = -45.0f,
                measuredNoisePeakDb = -38.0f,
                environmentDescription = "Городской поток / Умеренный шум",
                suggestedGateThresholdDb = -36.0f,
                suggestedSoftwareNsStrength = 0.70f,
                suggestedHpfCutoffHz = 90.0f,
                suggestedGainBoosterDb = 8.0f,
                previousGateThresholdDb = currentSettings.noiseGateThresholdDb,
                previousSoftwareNsStrength = currentSettings.softwareNsStrength,
                previousHpfCutoffHz = currentSettings.highPassCutoffHz
            )
        }

        val sortedRms = rmsSamples.sorted()
        val sortedPeaks = peakSamples.sorted()
        val n = sortedRms.size

        // Trimmed mean (middle 70% of distribution to eliminate speech or horns)
        val trimStart = (n * 0.15).toInt().coerceIn(0, n - 1)
        val trimEnd = (n * 0.85).toInt().coerceIn(trimStart + 1, n)
        val trimmedSubset = sortedRms.subList(trimStart, trimEnd)
        val avgNoiseRms = trimmedSubset.average().toFloat()

        // 90th percentile of noise peaks represents upper boundary of ambient cabin noise
        val p90Index = (n * 0.90).toInt().coerceIn(0, n - 1)
        val noiseCeilingPeak = sortedPeaks[p90Index]

        // Noise Gate Recommendation:
        // Set threshold +4.5 dB above ambient noise peak ceiling to prevent gate fluttering
        val suggestedGate = ((noiseCeilingPeak + 4.5f) * 10).roundToInt() / 10f
        val boundedGateThreshold = suggestedGate.coerceIn(-55.0f, -18.0f)

        // Noise Suppressor Strength Recommendation:
        val (suggestedNs, envDescription, suggestedHpf, suggestedGain) = when {
            avgNoiseRms > -33.0f -> {
                Quadruple(
                    0.90f,
                    "Трасса / Ветер и высокая скорость (>90 км/ч)",
                    115.0f,
                    6.0f
                )
            }
            avgNoiseRms > -42.0f -> {
                Quadruple(
                    0.75f,
                    "Городской поток / Плотный трафик и кондиционер",
                    95.0f,
                    9.0f
                )
            }
            avgNoiseRms > -50.0f -> {
                Quadruple(
                    0.55f,
                    "Спокойная езда / Тихий жилой район",
                    85.0f,
                    10.0f
                )
            }
            else -> {
                Quadruple(
                    0.35f,
                    "Тихий салон / Стоянка (минимальный фоновый шум)",
                    75.0f,
                    12.0f
                )
            }
        }

        return CalibrationResult(
            measuredNoiseRmsDb = (avgNoiseRms * 10).roundToInt() / 10f,
            measuredNoisePeakDb = (noiseCeilingPeak * 10).roundToInt() / 10f,
            environmentDescription = envDescription,
            suggestedGateThresholdDb = boundedGateThreshold,
            suggestedSoftwareNsStrength = suggestedNs,
            suggestedHpfCutoffHz = suggestedHpf,
            suggestedGainBoosterDb = suggestedGain,
            previousGateThresholdDb = currentSettings.noiseGateThresholdDb,
            previousSoftwareNsStrength = currentSettings.softwareNsStrength,
            previousHpfCutoffHz = currentSettings.highPassCutoffHz
        )
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
