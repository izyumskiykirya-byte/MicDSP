package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AudioInputType
import com.example.model.DspSettings
import com.example.model.PresetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dspDataStore: DataStore<Preferences> by preferencesDataStore(name = "car_dsp_preferences")

class DspPreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val SELECTED_INPUT_TYPE = stringPreferencesKey("selected_input_type")
        val HARDWARE_NS = booleanPreferencesKey("hardware_ns")
        val HARDWARE_AEC = booleanPreferencesKey("hardware_aec")
        val HARDWARE_AGC = booleanPreferencesKey("hardware_agc")
        val SOFTWARE_NS = booleanPreferencesKey("software_ns")
        val SOFTWARE_NS_STRENGTH = floatPreferencesKey("software_ns_strength")
        val HPF_ENABLED = booleanPreferencesKey("hpf_enabled")
        val HPF_CUTOFF = floatPreferencesKey("hpf_cutoff")
        val EQ_BAND_0 = floatPreferencesKey("eq_band_0")
        val EQ_BAND_1 = floatPreferencesKey("eq_band_1")
        val EQ_BAND_2 = floatPreferencesKey("eq_band_2")
        val EQ_BAND_3 = floatPreferencesKey("eq_band_3")
        val EQ_BAND_4 = floatPreferencesKey("eq_band_4")
        val NOISE_GATE_ENABLED = booleanPreferencesKey("noise_gate_enabled")
        val NOISE_GATE_THRESHOLD = floatPreferencesKey("noise_gate_threshold")
        val GAIN_BOOSTER = floatPreferencesKey("gain_booster")
        val LIMITER_ENABLED = booleanPreferencesKey("limiter_enabled")
        val LIMITER_THRESHOLD = floatPreferencesKey("limiter_threshold")
        val MONITOR_PLAYBACK = booleanPreferencesKey("monitor_playback")
        val AUTO_START_ON_BOOT = booleanPreferencesKey("auto_start_on_boot")
        val APPLY_LAST_PRESET_ON_LAUNCH = booleanPreferencesKey("apply_last_preset_on_launch")
        val ACTIVE_PRESET_NAME = stringPreferencesKey("active_preset_name")
    }

    val dspSettingsFlow: Flow<DspSettings> = context.dspDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val defaultSettings = PresetRepository.CITY.settings
            val inputTypeStr = preferences[PreferencesKeys.SELECTED_INPUT_TYPE] ?: AudioInputType.DEFAULT_MIC.name
            val inputType = try {
                AudioInputType.valueOf(inputTypeStr)
            } catch (_: Exception) {
                AudioInputType.DEFAULT_MIC
            }

            DspSettings(
                isEngineRunning = false,
                selectedInputType = inputType,
                hardwareNsEnabled = preferences[PreferencesKeys.HARDWARE_NS] ?: defaultSettings.hardwareNsEnabled,
                hardwareAecEnabled = preferences[PreferencesKeys.HARDWARE_AEC] ?: defaultSettings.hardwareAecEnabled,
                hardwareAgcEnabled = preferences[PreferencesKeys.HARDWARE_AGC] ?: defaultSettings.hardwareAgcEnabled,
                softwareNsEnabled = preferences[PreferencesKeys.SOFTWARE_NS] ?: defaultSettings.softwareNsEnabled,
                softwareNsStrength = preferences[PreferencesKeys.SOFTWARE_NS_STRENGTH] ?: defaultSettings.softwareNsStrength,
                highPassFilterEnabled = preferences[PreferencesKeys.HPF_ENABLED] ?: defaultSettings.highPassFilterEnabled,
                highPassCutoffHz = preferences[PreferencesKeys.HPF_CUTOFF] ?: defaultSettings.highPassCutoffHz,
                eqGainsDb = listOf(
                    preferences[PreferencesKeys.EQ_BAND_0] ?: defaultSettings.eqGainsDb[0],
                    preferences[PreferencesKeys.EQ_BAND_1] ?: defaultSettings.eqGainsDb[1],
                    preferences[PreferencesKeys.EQ_BAND_2] ?: defaultSettings.eqGainsDb[2],
                    preferences[PreferencesKeys.EQ_BAND_3] ?: defaultSettings.eqGainsDb[3],
                    preferences[PreferencesKeys.EQ_BAND_4] ?: defaultSettings.eqGainsDb[4],
                ),
                noiseGateEnabled = preferences[PreferencesKeys.NOISE_GATE_ENABLED] ?: defaultSettings.noiseGateEnabled,
                noiseGateThresholdDb = preferences[PreferencesKeys.NOISE_GATE_THRESHOLD] ?: defaultSettings.noiseGateThresholdDb,
                gainBoosterDb = preferences[PreferencesKeys.GAIN_BOOSTER] ?: defaultSettings.gainBoosterDb,
                limiterEnabled = preferences[PreferencesKeys.LIMITER_ENABLED] ?: defaultSettings.limiterEnabled,
                limiterThresholdDb = preferences[PreferencesKeys.LIMITER_THRESHOLD] ?: defaultSettings.limiterThresholdDb,
                monitorPlaybackEnabled = preferences[PreferencesKeys.MONITOR_PLAYBACK] ?: false,
                autoStartOnBoot = preferences[PreferencesKeys.AUTO_START_ON_BOOT] ?: true,
                applyLastPresetOnLaunch = preferences[PreferencesKeys.APPLY_LAST_PRESET_ON_LAUNCH] ?: true,
                activePresetName = preferences[PreferencesKeys.ACTIVE_PRESET_NAME] ?: defaultSettings.activePresetName
            )
        }

    suspend fun saveSettings(settings: DspSettings) {
        context.dspDataStore.edit { preferences ->
            preferences[PreferencesKeys.SELECTED_INPUT_TYPE] = settings.selectedInputType.name
            preferences[PreferencesKeys.HARDWARE_NS] = settings.hardwareNsEnabled
            preferences[PreferencesKeys.HARDWARE_AEC] = settings.hardwareAecEnabled
            preferences[PreferencesKeys.HARDWARE_AGC] = settings.hardwareAgcEnabled
            preferences[PreferencesKeys.SOFTWARE_NS] = settings.softwareNsEnabled
            preferences[PreferencesKeys.SOFTWARE_NS_STRENGTH] = settings.softwareNsStrength
            preferences[PreferencesKeys.HPF_ENABLED] = settings.highPassFilterEnabled
            preferences[PreferencesKeys.HPF_CUTOFF] = settings.highPassCutoffHz
            if (settings.eqGainsDb.size >= 5) {
                preferences[PreferencesKeys.EQ_BAND_0] = settings.eqGainsDb[0]
                preferences[PreferencesKeys.EQ_BAND_1] = settings.eqGainsDb[1]
                preferences[PreferencesKeys.EQ_BAND_2] = settings.eqGainsDb[2]
                preferences[PreferencesKeys.EQ_BAND_3] = settings.eqGainsDb[3]
                preferences[PreferencesKeys.EQ_BAND_4] = settings.eqGainsDb[4]
            }
            preferences[PreferencesKeys.NOISE_GATE_ENABLED] = settings.noiseGateEnabled
            preferences[PreferencesKeys.NOISE_GATE_THRESHOLD] = settings.noiseGateThresholdDb
            preferences[PreferencesKeys.GAIN_BOOSTER] = settings.gainBoosterDb
            preferences[PreferencesKeys.LIMITER_ENABLED] = settings.limiterEnabled
            preferences[PreferencesKeys.LIMITER_THRESHOLD] = settings.limiterThresholdDb
            preferences[PreferencesKeys.MONITOR_PLAYBACK] = settings.monitorPlaybackEnabled
            preferences[PreferencesKeys.AUTO_START_ON_BOOT] = settings.autoStartOnBoot
            preferences[PreferencesKeys.APPLY_LAST_PRESET_ON_LAUNCH] = settings.applyLastPresetOnLaunch
            preferences[PreferencesKeys.ACTIVE_PRESET_NAME] = settings.activePresetName
        }
    }
}
