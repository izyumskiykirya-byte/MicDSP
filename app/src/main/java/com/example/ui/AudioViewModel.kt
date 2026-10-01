package com.example.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DspPreferencesRepository
import com.example.dsp.NoiseCalibrator
import com.example.model.*
import com.example.service.AudioCaptureService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class DspTab(val title: String) {
    PRESETS("Пресеты"),
    CALIBRATION("Автокалибровка"),
    EQUALIZER("Эквалайзер"),
    DYNAMICS("Гейт и Компрессор"),
    HARDWARE("Железо и Роутинг")
}

data class DspUiState(
    val settings: DspSettings = PresetRepository.CITY.settings,
    val stats: AudioStats = AudioStats(),
    val isEngineRunning: Boolean = false,
    val selectedTab: DspTab = DspTab.PRESETS,
    val hasAudioPermission: Boolean = false,
    val availablePresets: List<AudioPreset> = PresetRepository.ALL_PRESETS
)

class AudioViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DspPreferencesRepository(application)
    private val context = application.applicationContext

    private val _uiState = MutableStateFlow(DspUiState())
    val uiState: StateFlow<DspUiState> = _uiState.asStateFlow()

    // Auto-calibration state
    private val _calibrationState = MutableStateFlow<CalibrationState>(CalibrationState.Idle)
    val calibrationState: StateFlow<CalibrationState> = _calibrationState.asStateFlow()

    private val noiseCalibrator = NoiseCalibrator()
    private var calibrationJob: Job? = null

    private var audioService: AudioCaptureService? = null
    private var isBound = false
    private var statsJob: Job? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? AudioCaptureService.LocalBinder
            audioService = binder?.getService()
            isBound = true
            checkEngineStatus()
            observeStats()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            audioService = null
            isBound = false
            statsJob?.cancel()
            _uiState.update { it.copy(isEngineRunning = false) }
        }
    }

    init {
        // Load persisted settings and automatically apply last preset if enabled
        viewModelScope.launch {
            repository.dspSettingsFlow.collect { savedSettings ->
                val effectiveSettings = if (savedSettings.applyLastPresetOnLaunch) {
                    val foundPreset = PresetRepository.findPresetByName(savedSettings.activePresetName)
                    if (foundPreset != null && savedSettings.activePresetName != "Пользовательский" && savedSettings.activePresetName != "Откалибровано (Авто)") {
                        foundPreset.settings.copy(
                            selectedInputType = savedSettings.selectedInputType,
                            autoStartOnBoot = savedSettings.autoStartOnBoot,
                            applyLastPresetOnLaunch = savedSettings.applyLastPresetOnLaunch,
                            activePresetName = savedSettings.activePresetName
                        )
                    } else {
                        savedSettings
                    }
                } else {
                    PresetRepository.CITY.settings.copy(
                        selectedInputType = savedSettings.selectedInputType,
                        autoStartOnBoot = savedSettings.autoStartOnBoot,
                        applyLastPresetOnLaunch = false
                    )
                }

                _uiState.update { current ->
                    current.copy(
                        settings = effectiveSettings,
                        isEngineRunning = audioService?.isEngineActive() ?: AudioCaptureService.activeEngine?.isEngineRunning() ?: false
                    )
                }
                audioService?.updateSettings(effectiveSettings)
                AudioCaptureService.activeEngine?.updateSettings(effectiveSettings)
            }
        }

        // Try binding if service is already running
        bindService()

        // Polling engine state fallback
        viewModelScope.launch {
            while (true) {
                checkEngineStatus()
                delay(1000)
            }
        }
    }

    private fun bindService() {
        val intent = Intent(context, AudioCaptureService::class.java)
        try {
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (_: Exception) {}
    }

    private fun checkEngineStatus() {
        val running = audioService?.isEngineActive() ?: AudioCaptureService.activeEngine?.isEngineRunning() ?: false
        if (_uiState.value.isEngineRunning != running) {
            _uiState.update { it.copy(isEngineRunning = running) }
        }
        if (running && statsJob == null) {
            observeStats()
        }
    }

    private fun observeStats() {
        statsJob?.cancel()
        val statsFlow = audioService?.getAudioStatsFlow() ?: AudioCaptureService.activeEngine?.audioStats
        if (statsFlow != null) {
            statsJob = viewModelScope.launch {
                statsFlow.collect { newStats ->
                    _uiState.update { it.copy(stats = newStats) }
                }
            }
        }
    }

    fun selectTab(tab: DspTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun setAudioPermissionGranted(granted: Boolean) {
        _uiState.update { it.copy(hasAudioPermission = granted) }
    }

    fun toggleEngine() {
        val currentlyRunning = _uiState.value.isEngineRunning
        val intent = Intent(context, AudioCaptureService::class.java)
        if (currentlyRunning) {
            intent.action = AudioCaptureService.ACTION_STOP
            context.startService(intent)
            _uiState.update { it.copy(isEngineRunning = false) }
        } else {
            intent.action = AudioCaptureService.ACTION_START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            bindService()
            _uiState.update { it.copy(isEngineRunning = true) }
            observeStats()
        }
    }

    fun applyPreset(preset: AudioPreset) {
        val updated = preset.settings.copy(
            selectedInputType = _uiState.value.settings.selectedInputType,
            autoStartOnBoot = _uiState.value.settings.autoStartOnBoot,
            monitorPlaybackEnabled = _uiState.value.settings.monitorPlaybackEnabled,
            activePresetName = preset.name
        )
        updateAndSaveSettings(updated)
    }

    fun setInputType(inputType: AudioInputType) {
        val updated = _uiState.value.settings.copy(selectedInputType = inputType)
        updateAndSaveSettings(updated)
    }

    fun setGainBooster(gainDb: Float) {
        val updated = _uiState.value.settings.copy(
            gainBoosterDb = gainDb,
            activePresetName = "Пользовательский"
        )
        updateAndSaveSettings(updated)
    }

    fun setHpfEnabled(enabled: Boolean) {
        val updated = _uiState.value.settings.copy(
            highPassFilterEnabled = enabled,
            activePresetName = "Пользовательский"
        )
        updateAndSaveSettings(updated)
    }

    fun setHpfCutoff(cutoffHz: Float) {
        val updated = _uiState.value.settings.copy(
            highPassCutoffHz = cutoffHz,
            activePresetName = "Пользовательский"
        )
        updateAndSaveSettings(updated)
    }

    fun setEqBandGain(bandIndex: Int, gainDb: Float) {
        val currentGains = _uiState.value.settings.eqGainsDb.toMutableList()
        if (bandIndex in 0 until currentGains.size) {
            currentGains[bandIndex] = gainDb
            val updated = _uiState.value.settings.copy(
                eqGainsDb = currentGains,
                activePresetName = "Пользовательский"
            )
            updateAndSaveSettings(updated)
        }
    }

    fun setNoiseGateEnabled(enabled: Boolean) {
        val updated = _uiState.value.settings.copy(
            noiseGateEnabled = enabled,
            activePresetName = "Пользовательский"
        )
        updateAndSaveSettings(updated)
    }

    fun setNoiseGateThreshold(thresholdDb: Float) {
        val updated = _uiState.value.settings.copy(
            noiseGateThresholdDb = thresholdDb,
            activePresetName = "Пользовательский"
        )
        updateAndSaveSettings(updated)
    }

    fun setLimiterEnabled(enabled: Boolean) {
        val updated = _uiState.value.settings.copy(
            limiterEnabled = enabled,
            activePresetName = "Пользовательский"
        )
        updateAndSaveSettings(updated)
    }

    fun setLimiterThreshold(thresholdDb: Float) {
        val updated = _uiState.value.settings.copy(
            limiterThresholdDb = thresholdDb,
            activePresetName = "Пользовательский"
        )
        updateAndSaveSettings(updated)
    }

    fun setHardwareNsEnabled(enabled: Boolean) {
        val updated = _uiState.value.settings.copy(
            hardwareNsEnabled = enabled,
            activePresetName = "Пользовательский"
        )
        updateAndSaveSettings(updated)
    }

    fun setHardwareAecEnabled(enabled: Boolean) {
        val updated = _uiState.value.settings.copy(
            hardwareAecEnabled = enabled,
            activePresetName = "Пользовательский"
        )
        updateAndSaveSettings(updated)
    }

    fun setHardwareAgcEnabled(enabled: Boolean) {
        val updated = _uiState.value.settings.copy(
            hardwareAgcEnabled = enabled,
            activePresetName = "Пользовательский"
        )
        updateAndSaveSettings(updated)
    }

    fun setSoftwareNsEnabled(enabled: Boolean) {
        val updated = _uiState.value.settings.copy(
            softwareNsEnabled = enabled,
            activePresetName = "Пользовательский"
        )
        updateAndSaveSettings(updated)
    }

    fun setSoftwareNsStrength(strength: Float) {
        val updated = _uiState.value.settings.copy(
            softwareNsStrength = strength,
            activePresetName = "Пользовательский"
        )
        updateAndSaveSettings(updated)
    }

    fun setMonitorPlayback(enabled: Boolean) {
        val updated = _uiState.value.settings.copy(monitorPlaybackEnabled = enabled)
        updateAndSaveSettings(updated)
    }

    fun setAutoStartOnBoot(enabled: Boolean) {
        val updated = _uiState.value.settings.copy(autoStartOnBoot = enabled)
        updateAndSaveSettings(updated)
    }

    fun setApplyLastPresetOnLaunch(enabled: Boolean) {
        val updated = _uiState.value.settings.copy(applyLastPresetOnLaunch = enabled)
        updateAndSaveSettings(updated)
    }

    // ==========================================
    // 10-Second Auto-Calibration Operations
    // ==========================================

    fun startAutoCalibration() {
        calibrationJob?.cancel()

        // Ensure audio engine is capturing
        if (!_uiState.value.isEngineRunning) {
            toggleEngine()
        }

        noiseCalibrator.reset()

        calibrationJob = viewModelScope.launch {
            val totalDurationMs = 10000L
            val sampleIntervalMs = 50L
            val totalSteps = (totalDurationMs / sampleIntervalMs).toInt()
            var currentStep = 0

            _calibrationState.value = CalibrationState.InProgress(
                remainingSeconds = 10,
                progress = 0.0f,
                currentNoiseRmsDb = _uiState.value.stats.inputRmsDb,
                currentNoisePeakDb = _uiState.value.stats.inputPeakDb,
                samplesCollected = 0
            )

            while (currentStep < totalSteps) {
                delay(sampleIntervalMs)
                currentStep++

                val rms = _uiState.value.stats.inputRmsDb
                val peak = _uiState.value.stats.inputPeakDb
                noiseCalibrator.addSample(rms, peak)

                val elapsedMs = currentStep * sampleIntervalMs
                val remainingSeconds = (((totalDurationMs - elapsedMs) + 999) / 1000L).toInt().coerceAtLeast(0)
                val progress = (elapsedMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0.0f, 1.0f)

                _calibrationState.value = CalibrationState.InProgress(
                    remainingSeconds = remainingSeconds,
                    progress = progress,
                    currentNoiseRmsDb = rms,
                    currentNoisePeakDb = peak,
                    samplesCollected = noiseCalibrator.getSampleCount()
                )
            }

            // Analysis complete
            val result = noiseCalibrator.computeResult(_uiState.value.settings)
            _calibrationState.value = CalibrationState.Completed(result)
        }
    }

    fun cancelAutoCalibration() {
        calibrationJob?.cancel()
        calibrationJob = null
        _calibrationState.value = CalibrationState.Idle
    }

    fun applyCalibrationResult(result: CalibrationResult) {
        val updated = _uiState.value.settings.copy(
            noiseGateEnabled = true,
            noiseGateThresholdDb = result.suggestedGateThresholdDb,
            softwareNsEnabled = true,
            softwareNsStrength = result.suggestedSoftwareNsStrength,
            highPassFilterEnabled = true,
            highPassCutoffHz = result.suggestedHpfCutoffHz,
            gainBoosterDb = result.suggestedGainBoosterDb,
            activePresetName = "Откалибровано (Авто)"
        )
        updateAndSaveSettings(updated)
        _calibrationState.value = CalibrationState.Idle
    }

    fun dismissCalibration() {
        cancelAutoCalibration()
    }

    private fun updateAndSaveSettings(newSettings: DspSettings) {
        _uiState.update { it.copy(settings = newSettings) }
        audioService?.updateSettings(newSettings)
        AudioCaptureService.activeEngine?.updateSettings(newSettings)
        viewModelScope.launch {
            repository.saveSettings(newSettings)
        }
    }

    override fun onCleared() {
        super.onCleared()
        calibrationJob?.cancel()
        if (isBound) {
            try {
                context.unbindService(serviceConnection)
            } catch (_: Exception) {}
            isBound = false
        }
        statsJob?.cancel()
    }
}
