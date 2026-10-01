package com.example.model

import android.media.AudioDeviceInfo

/**
 * Audio input source selection for car head unit
 */
enum class AudioInputType(val displayName: String, val deviceType: Int?) {
    DEFAULT_MIC("Встроенный микрофон ГУ", AudioDeviceInfo.TYPE_BUILTIN_MIC),
    EXTERNAL_JACK("Внешний 3.5мм Jack", AudioDeviceInfo.TYPE_WIRED_HEADSET),
    USB_AUDIO("USB Аудиокарта / Микрофон", AudioDeviceInfo.TYPE_USB_DEVICE),
    BLUETOOTH_SCO("Bluetooth Гарнитура (SCO)", AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
}

/**
 * Complete DSP processing configuration
 */
data class DspSettings(
    val isEngineRunning: Boolean = false,
    val selectedInputType: AudioInputType = AudioInputType.DEFAULT_MIC,
    val sampleRate: Int = 48000,

    // Hardware AudioFx (Android OS / SoC DSP)
    val hardwareNsEnabled: Boolean = true,
    val hardwareAecEnabled: Boolean = true,
    val hardwareAgcEnabled: Boolean = false,

    // Software Fallback & Advanced DSP
    val softwareNsEnabled: Boolean = true,
    val softwareNsStrength: Float = 0.75f, // 0.0 to 1.0

    // Low Cut / High Pass Filter (removes engine rumble & road vibrations)
    val highPassFilterEnabled: Boolean = true,
    val highPassCutoffHz: Float = 90.0f, // 60Hz to 160Hz

    // 5-band Equalizer gains in dB (-12 dB to +12 dB)
    // Band 0: 80 Hz (Low Rumble)
    // Band 1: 250 Hz (Cabin Resonance / Boom)
    // Band 2: 1000 Hz (Vocal Core / Intelligibility)
    // Band 3: 3500 Hz (Consonant Clarity)
    // Band 4: 8000 Hz (Air / Presence)
    val eqGainsDb: List<Float> = listOf(-3.0f, -4.0f, +2.0f, +4.0f, +1.0f),

    // Noise Gate
    val noiseGateEnabled: Boolean = true,
    val noiseGateThresholdDb: Float = -38.0f, // -60dB to -15dB
    val noiseGateAttackMs: Float = 8.0f,
    val noiseGateReleaseMs: Float = 140.0f,

    // Input Gain Booster (0 to +30 dB)
    val gainBoosterDb: Float = 8.0f,

    // Dynamic Limiter / Compressor
    val limiterEnabled: Boolean = true,
    val limiterThresholdDb: Float = -1.5f, // -12dB to 0dB

    // Realtime audio monitoring via AudioTrack
    val monitorPlaybackEnabled: Boolean = false,

    // System integration & Startup
    val autoStartOnBoot: Boolean = true,
    val applyLastPresetOnLaunch: Boolean = true,
    val activePresetName: String = "Город / Шумная дорога"
)

/**
 * Automotive Audio DSP Presets
 */
data class AudioPreset(
    val name: String,
    val description: String,
    val settings: DspSettings
)

object PresetRepository {
    val CITY = AudioPreset(
        name = "Город / Шумная дорога",
        description = "Подавление шума покрышек, двигателя и кондиционера. Акцент на разборчивость речи.",
        settings = DspSettings(
            hardwareNsEnabled = true,
            hardwareAecEnabled = true,
            hardwareAgcEnabled = false,
            softwareNsEnabled = true,
            softwareNsStrength = 0.70f,
            highPassFilterEnabled = true,
            highPassCutoffHz = 95.0f,
            eqGainsDb = listOf(-4.0f, -5.0f, +2.5f, +4.5f, +1.0f),
            noiseGateEnabled = true,
            noiseGateThresholdDb = -36.0f,
            gainBoosterDb = 9.0f,
            limiterEnabled = true,
            limiterThresholdDb = -1.5f,
            autoStartOnBoot = true,
            applyLastPresetOnLaunch = true,
            activePresetName = "Город / Шумная дорога"
        )
    )

    val HIGHWAY = AudioPreset(
        name = "Трасса (Высокая скорость)",
        description = "Максимальная фильтрация аэродинамического свиста и гула на скорости свыше 90 км/ч.",
        settings = DspSettings(
            hardwareNsEnabled = true,
            hardwareAecEnabled = true,
            hardwareAgcEnabled = false,
            softwareNsEnabled = true,
            softwareNsStrength = 0.90f,
            highPassFilterEnabled = true,
            highPassCutoffHz = 120.0f,
            eqGainsDb = listOf(-6.0f, -6.0f, +3.0f, +5.5f, -1.0f),
            noiseGateEnabled = true,
            noiseGateThresholdDb = -32.0f,
            gainBoosterDb = 13.0f,
            limiterEnabled = true,
            limiterThresholdDb = -2.0f,
            autoStartOnBoot = true,
            applyLastPresetOnLaunch = true,
            activePresetName = "Трасса (Высокая скорость)"
        )
    )

    val QUIET_CABIN = AudioPreset(
        name = "Тихий салон (Стоянка)",
        description = "Минимальная агрессивность фильтров, естественное теплое звучание голоса.",
        settings = DspSettings(
            hardwareNsEnabled = true,
            hardwareAecEnabled = false,
            hardwareAgcEnabled = false,
            softwareNsEnabled = false,
            softwareNsStrength = 0.30f,
            highPassFilterEnabled = true,
            highPassCutoffHz = 70.0f,
            eqGainsDb = listOf(0.0f, -1.0f, +1.5f, +2.0f, +2.0f),
            noiseGateEnabled = true,
            noiseGateThresholdDb = -48.0f,
            gainBoosterDb = 4.0f,
            limiterEnabled = true,
            limiterThresholdDb = -1.0f,
            autoStartOnBoot = true,
            applyLastPresetOnLaunch = true,
            activePresetName = "Тихий салон (Стоянка)"
        )
    )

    val STUDIO_VOICE = AudioPreset(
        name = "Студийный голос",
        description = "Плотный вещательный звук с компрессией и чистым верхом.",
        settings = DspSettings(
            hardwareNsEnabled = false,
            hardwareAecEnabled = false,
            hardwareAgcEnabled = false,
            softwareNsEnabled = true,
            softwareNsStrength = 0.40f,
            highPassFilterEnabled = true,
            highPassCutoffHz = 80.0f,
            eqGainsDb = listOf(-1.0f, -2.0f, +3.0f, +3.5f, +3.0f),
            noiseGateEnabled = true,
            noiseGateThresholdDb = -44.0f,
            gainBoosterDb = 6.0f,
            limiterEnabled = true,
            limiterThresholdDb = -1.0f,
            autoStartOnBoot = true,
            applyLastPresetOnLaunch = true,
            activePresetName = "Студийный голос"
        )
    )

    val ALL_PRESETS = listOf(CITY, HIGHWAY, QUIET_CABIN, STUDIO_VOICE)

    fun findPresetByName(name: String): AudioPreset? {
        return ALL_PRESETS.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}

/**
 * Real-time telemetry & VU-meter stats emitted during audio processing
 */
data class AudioStats(
    val inputRmsDb: Float = -100.0f,
    val inputPeakDb: Float = -100.0f,
    val outputRmsDb: Float = -100.0f,
    val outputPeakDb: Float = -100.0f,
    val isGateOpen: Boolean = false,
    val isClipping: Boolean = false,
    val hardwareNsAvailable: Boolean = false,
    val hardwareAecAvailable: Boolean = false,
    val hardwareAgcAvailable: Boolean = false,
    val hardwareNsActive: Boolean = false,
    val hardwareAecActive: Boolean = false,
    val hardwareAgcActive: Boolean = false,
    val framesProcessedTotal: Long = 0L,
    val activeInputDeviceName: String = "Микрофон"
)

/**
 * 10-Second Auto-Calibration State and Analytical Results
 */
sealed class CalibrationState {
    object Idle : CalibrationState()

    data class InProgress(
        val remainingSeconds: Int,
        val progress: Float, // 0.0 to 1.0
        val currentNoiseRmsDb: Float,
        val currentNoisePeakDb: Float,
        val samplesCollected: Int
    ) : CalibrationState()

    data class Completed(
        val result: CalibrationResult
    ) : CalibrationState()

    data class Error(val message: String) : CalibrationState()
}

data class CalibrationResult(
    val measuredNoiseRmsDb: Float,
    val measuredNoisePeakDb: Float,
    val environmentDescription: String,
    val suggestedGateThresholdDb: Float,
    val suggestedSoftwareNsStrength: Float,
    val suggestedHpfCutoffHz: Float,
    val suggestedGainBoosterDb: Float,
    val previousGateThresholdDb: Float,
    val previousSoftwareNsStrength: Float,
    val previousHpfCutoffHz: Float
)
