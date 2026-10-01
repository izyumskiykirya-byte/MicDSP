package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AudioInputType
import com.example.model.AudioPreset
import com.example.model.CalibrationState
import com.example.ui.components.*
import com.example.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun MainCarScreen(
    viewModel: AudioViewModel,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val calibrationState by viewModel.calibrationState.collectAsState()
    val settings = uiState.settings
    val stats = uiState.stats

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("main_car_screen"),
        color = CarBackground
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Top Header: App Brand, Quick Calibrate Action, Master Engine Toggle
            CarTopBar(
                isRunning = uiState.isEngineRunning,
                onToggleEngine = { viewModel.toggleEngine() },
                onQuickCalibrate = {
                    viewModel.selectTab(DspTab.CALIBRATION)
                    viewModel.startAutoCalibration()
                },
                hasPermission = uiState.hasAudioPermission,
                onRequestPermission = onRequestPermission,
                activePreset = settings.activePresetName,
                sampleRate = settings.sampleRate
            )

            // Permission Warning Banner if missing
            if (!uiState.hasAudioPermission) {
                CarPermissionBanner(onRequestPermission = onRequestPermission)
            }

            // Main Content: Landscape Dual-Pane
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // LEFT PANE: Telemetry, Master VU-Meter, Noise Gate LED & Quick Toggles (Fixed ~320dp width)
                Card(
                    modifier = Modifier
                        .width(320.dp)
                        .fillMaxHeight(),
                    colors = CardDefaults.cardColors(containerColor = CarSurface),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CarCardBorder)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "МОНИТОРИНГ СИГНАЛА",
                            color = CarCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        // Input VU-Meter
                        AutomotiveVuMeter(
                            label = "ВХОД (ДО DSP)",
                            rmsDb = stats.inputRmsDb,
                            peakDb = stats.inputPeakDb,
                            isClipping = stats.isClipping
                        )

                        // Output VU-Meter
                        AutomotiveVuMeter(
                            label = "ВЫХОД (ПОСЛЕ DSP)",
                            rmsDb = stats.outputRmsDb,
                            peakDb = stats.outputPeakDb,
                            isClipping = stats.isClipping
                        )

                        // Dynamic LEDs: Noise Gate State & Limiter Overload
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatusLedPill(
                                label = "ГЕЙТ",
                                isActive = stats.isGateOpen,
                                activeColor = CarGreen,
                                inactiveColor = CarAmber,
                                activeText = "РЕЧЬ",
                                inactiveText = "ШУМ СРЕЗАН",
                                modifier = Modifier.weight(1f)
                            )
                            StatusLedPill(
                                label = "ЛИМИТЕР",
                                isActive = stats.isClipping,
                                activeColor = CarRedBright,
                                inactiveColor = CarTextMuted,
                                activeText = "ПЕРЕГРУЗ",
                                inactiveText = "НОРМА",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Hardware SoC capabilities badge
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(CarSurfaceVariant)
                                .border(1.dp, CarCardBorder, RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "АППАРАТНЫЙ ЧИП МАГНИТОЛЫ:",
                                color = CarTextSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "AEC (Эхоподавление):",
                                    color = CarTextSecondary,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = if (stats.hardwareAecAvailable) "ПОДДЕРЖИВАЕТСЯ" else "НЕТ (Софт DSP)",
                                    color = if (stats.hardwareAecAvailable) CarGreen else CarAmber,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "NS (Шумоподавление):",
                                    color = CarTextSecondary,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = if (stats.hardwareNsAvailable) "ПОДДЕРЖИВАЕТСЯ" else "НЕТ (Софт DSP)",
                                    color = if (stats.hardwareNsAvailable) CarGreen else CarAmber,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Output Monitoring (Headphones/Loopback check)
                        CarSwitchCard(
                            title = "Мониторинг в реальном времени",
                            subtitle = "Слушать обработку в динамиках",
                            checked = settings.monitorPlaybackEnabled,
                            onCheckedChange = { viewModel.setMonitorPlayback(it) },
                            testTag = "monitor_playback_toggle"
                        )

                        // Active device info
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF090E17))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = CarCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stats.activeInputDeviceName,
                                color = CarTextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // RIGHT PANE: Car-friendly Navigation Tabs & Settings Content
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    colors = CardDefaults.cardColors(containerColor = CarSurface),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CarCardBorder)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Horizontal Tab Selector
                        CarTabs(
                            selectedTab = uiState.selectedTab,
                            onSelectTab = { viewModel.selectTab(it) }
                        )

                        // Tab Content Body
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                        ) {
                            when (uiState.selectedTab) {
                                DspTab.PRESETS -> PresetsTabContent(
                                    presets = uiState.availablePresets,
                                    activePresetName = settings.activePresetName,
                                    onSelectPreset = { viewModel.applyPreset(it) },
                                    onStartCalibration = {
                                        viewModel.selectTab(DspTab.CALIBRATION)
                                        viewModel.startAutoCalibration()
                                    }
                                )
                                DspTab.CALIBRATION -> CalibrationTabContent(
                                    calibrationState = calibrationState,
                                    onStartCalibration = { viewModel.startAutoCalibration() },
                                    onCancelCalibration = { viewModel.cancelAutoCalibration() },
                                    onApplyResult = { viewModel.applyCalibrationResult(it) }
                                )
                                DspTab.EQUALIZER -> EqualizerTabContent(
                                    settings = settings,
                                    onHpfToggle = { viewModel.setHpfEnabled(it) },
                                    onHpfCutoffChange = { viewModel.setHpfCutoff(it) },
                                    onBandGainChange = { index, gain -> viewModel.setEqBandGain(index, gain) }
                                )
                                DspTab.DYNAMICS -> DynamicsTabContent(
                                    settings = settings,
                                    onGainChange = { viewModel.setGainBooster(it) },
                                    onGateToggle = { viewModel.setNoiseGateEnabled(it) },
                                    onGateThresholdChange = { viewModel.setNoiseGateThreshold(it) },
                                    onLimiterToggle = { viewModel.setLimiterEnabled(it) },
                                    onLimiterThresholdChange = { viewModel.setLimiterThreshold(it) }
                                )
                                DspTab.HARDWARE -> HardwareTabContent(
                                    settings = settings,
                                    stats = stats,
                                    onSelectInput = { viewModel.setInputType(it) },
                                    onHardwareNsToggle = { viewModel.setHardwareNsEnabled(it) },
                                    onHardwareAecToggle = { viewModel.setHardwareAecEnabled(it) },
                                    onHardwareAgcToggle = { viewModel.setHardwareAgcEnabled(it) },
                                    onSoftwareNsToggle = { viewModel.setSoftwareNsEnabled(it) },
                                    onSoftwareNsStrengthChange = { viewModel.setSoftwareNsStrength(it) },
                                    onAutoStartToggle = { viewModel.setAutoStartOnBoot(it) },
                                    onApplyLastPresetToggle = { viewModel.setApplyLastPresetOnLaunch(it) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Car Top Bar with large tactile buttons
 */
@Composable
private fun CarTopBar(
    isRunning: Boolean,
    onToggleEngine: () -> Unit,
    onQuickCalibrate: () -> Unit,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    activePreset: String,
    sampleRate: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CarSurface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App Title & Status
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isRunning) CarCyan else CarSurfaceVariant)
                    .border(1.dp, CarCardBorder, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = if (isRunning) Color.Black else CarTextSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "АВТОЗВУК DSP",
                        color = CarTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isRunning) CarGreen.copy(alpha = 0.2f) else CarRed.copy(alpha = 0.2f))
                            .border(1.dp, if (isRunning) CarGreen else CarRed, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isRunning) "ОНЛАЙН" else "ОСТАНОВЛЕН",
                            color = if (isRunning) CarGreen else CarRed,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = "Пресет: $activePreset • $sampleRate Гц 16-бит",
                    color = CarTextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        // Action Buttons: Quick Auto-Calibration and Engine Master Switch
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Quick Auto-Calibration Button
            Button(
                onClick = {
                    if (!hasPermission) {
                        onRequestPermission()
                    } else {
                        onQuickCalibrate()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1E2E42),
                    contentColor = CarCyan
                ),
                border = ButtonDefaults.outlinedButtonBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CarCyan)),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("top_bar_calibrate_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Sensors,
                    contentDescription = null,
                    tint = CarCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "КАЛИБРОВКА (10 СЕК)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            // Giant Master Switch for Driver Safety
            Button(
                onClick = {
                    if (!hasPermission) {
                        onRequestPermission()
                    } else {
                        onToggleEngine()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) CarRed else CarCyan,
                    contentColor = if (isRunning) Color.White else Color.Black
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("engine_toggle_button")
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isRunning) "ВЫКЛЮЧИТЬ DSP" else "ЗАПУСТИТЬ DSP",
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp
                )
            }
        }
    }
}

/**
 * Horizontal Car Tabs
 */
@Composable
private fun CarTabs(
    selectedTab: DspTab,
    onSelectTab: (DspTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF090E17))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        DspTab.values().forEach { tab ->
            val isSelected = tab == selectedTab
            val isSpecialTab = tab == DspTab.CALIBRATION
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        when {
                            isSelected -> CarCyan
                            isSpecialTab -> Color(0xFF132338)
                            else -> CarSurfaceVariant
                        }
                    )
                    .border(
                        1.dp,
                        when {
                            isSelected -> CarCyan
                            isSpecialTab -> CarCyan.copy(alpha = 0.5f)
                            else -> CarCardBorder
                        },
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { onSelectTab(tab) }
                    .testTag("tab_${tab.name}"),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSpecialTab) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = null,
                            tint = if (isSelected) Color.Black else CarCyan,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = tab.title,
                        color = when {
                            isSelected -> Color.Black
                            isSpecialTab -> CarCyan
                            else -> CarTextPrimary
                        },
                        fontSize = 12.sp,
                        fontWeight = if (isSelected || isSpecialTab) FontWeight.Black else FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * Tab 1: Presets
 */
@Composable
private fun PresetsTabContent(
    presets: List<AudioPreset>,
    activePresetName: String,
    onSelectPreset: (AudioPreset) -> Unit,
    onStartCalibration: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Quick Auto-Calibration Banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0F2538))
                .border(1.5.dp, CarCyan, RoundedCornerShape(12.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Sensors,
                        contentDescription = null,
                        tint = CarCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "АВТОКАЛИБРОВКА ПОД САЛОН",
                        color = CarCyan,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Замеряет текущий шум дороги и кондиционера за 10 сек и вычисляет идеальный порог гейта.",
                    color = CarTextSecondary,
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Button(
                onClick = onStartCalibration,
                colors = ButtonDefaults.buttonColors(containerColor = CarCyan, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(text = "ЗАМЕР 10 СЕК", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        Text(
            text = "ГОТОВЫЕ АВТОМОБИЛЬНЫЕ ПРЕСЕТЫ",
            color = CarTextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )

        presets.forEach { preset ->
            val isCurrent = preset.name == activePresetName
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isCurrent) Color(0xFF0C2433) else CarSurfaceVariant)
                    .border(
                        2.dp,
                        if (isCurrent) CarCyan else CarCardBorder,
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelectPreset(preset) }
                    .padding(16.dp)
                    .testTag("preset_${preset.name}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = preset.name,
                            color = if (isCurrent) CarCyan else CarTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (isCurrent) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(CarCyan)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "АКТИВЕН",
                                    color = Color.Black,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = preset.description,
                        color = CarTextSecondary,
                        fontSize = 13.sp
                    )
                }

                RadioButton(
                    selected = isCurrent,
                    onClick = { onSelectPreset(preset) },
                    colors = RadioButtonDefaults.colors(
                        selectedColor = CarCyan,
                        unselectedColor = CarTextMuted
                    )
                )
            }
        }
    }
}

/**
 * Tab 3: Equalizer
 */
@Composable
private fun EqualizerTabContent(
    settings: com.example.model.DspSettings,
    onHpfToggle: (Boolean) -> Unit,
    onHpfCutoffChange: (Float) -> Unit,
    onBandGainChange: (Int, Float) -> Unit
) {
    val bandNames = listOf(
        "80 Гц (Гул подвески / Саб)",
        "250 Гц (Резонанс салона / Бубнеж)",
        "1000 Гц (Основа голоса)",
        "3500 Гц (Разборчивость речи)",
        "8000 Гц (Воздух / Присутствие)"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // High-Pass Filter Card
        CarSwitchCard(
            title = "High-Pass Filter (Low-Cut)",
            subtitle = "Срезает низкочастотный гул двигателя, выхлопа и вибрацию кузова",
            checked = settings.highPassFilterEnabled,
            onCheckedChange = onHpfToggle,
            badgeText = "80–120 Гц",
            testTag = "hpf_toggle"
        )

        AnimatedVisibility(visible = settings.highPassFilterEnabled) {
            CarSlider(
                label = "Частота среза Low-Cut",
                value = settings.highPassCutoffHz,
                valueRange = 60f..160f,
                unit = "Гц",
                onValueChange = onHpfCutoffChange,
                testTag = "hpf_cutoff_slider"
            )
        }

        Text(
            text = "5-ПОЛОСНЫЙ ПАРАМЕТРИЧЕСКИЙ ЭКВАЛАЙЗЕР РЕЧИ",
            color = CarTextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )

        // 5 Bands Sliders
        for (i in 0 until 5) {
            val gain = settings.eqGainsDb.getOrElse(i) { 0f }
            CarSlider(
                label = bandNames[i],
                value = gain,
                valueRange = -12f..12f,
                unit = "дБ",
                onValueChange = { onBandGainChange(i, it) },
                testTag = "eq_band_$i"
            )
        }
    }
}

/**
 * Tab 4: Dynamics (Gate, Limiter, Booster)
 */
@Composable
private fun DynamicsTabContent(
    settings: com.example.model.DspSettings,
    onGainChange: (Float) -> Unit,
    onGateToggle: (Boolean) -> Unit,
    onGateThresholdChange: (Float) -> Unit,
    onLimiterToggle: (Boolean) -> Unit,
    onLimiterThresholdChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Gain Booster
        CarSlider(
            label = "Программный Gain Booster (Усиление микрофона)",
            value = settings.gainBoosterDb,
            valueRange = 0f..30f,
            unit = "дБ",
            onValueChange = onGainChange,
            testTag = "gain_booster_slider"
        )

        // Noise Gate
        CarSwitchCard(
            title = "Шумовой гейт (Noise Gate)",
            subtitle = "Глушит микрофон в паузах речи, полностью отсекая шум салона и кондиционера",
            checked = settings.noiseGateEnabled,
            onCheckedChange = onGateToggle,
            badgeText = "Adaptive",
            testTag = "noise_gate_toggle"
        )

        AnimatedVisibility(visible = settings.noiseGateEnabled) {
            CarSlider(
                label = "Порог срабатывания гейта (Threshold)",
                value = settings.noiseGateThresholdDb,
                valueRange = -60f..-15f,
                unit = "дБ",
                onValueChange = onGateThresholdChange,
                testTag = "noise_gate_threshold_slider"
            )
        }

        // Compressor / Limiter
        CarSwitchCard(
            title = "Динамический лимитер (Anti-Clipping)",
            subtitle = "Мягкая компрессия и защита от треска/перегруза при громком крике за рулем",
            checked = settings.limiterEnabled,
            onCheckedChange = onLimiterToggle,
            badgeText = "Peak Guard",
            testTag = "limiter_toggle"
        )

        AnimatedVisibility(visible = settings.limiterEnabled) {
            CarSlider(
                label = "Порог лимитирования (Ceiling)",
                value = settings.limiterThresholdDb,
                valueRange = -12f..0f,
                unit = "дБ",
                onValueChange = onLimiterThresholdChange,
                testTag = "limiter_threshold_slider"
            )
        }
    }
}

/**
 * Tab 5: Hardware & Audio Routing
 */
@Composable
private fun HardwareTabContent(
    settings: com.example.model.DspSettings,
    stats: com.example.model.AudioStats,
    onSelectInput: (AudioInputType) -> Unit,
    onHardwareNsToggle: (Boolean) -> Unit,
    onHardwareAecToggle: (Boolean) -> Unit,
    onHardwareAgcToggle: (Boolean) -> Unit,
    onSoftwareNsToggle: (Boolean) -> Unit,
    onSoftwareNsStrengthChange: (Float) -> Unit,
    onAutoStartToggle: (Boolean) -> Unit,
    onApplyLastPresetToggle: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "ИСТОЧНИК ВХОДНОГО СИГНАЛА (АУДИО ВХОД)",
            color = CarTextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )

        // Device Selection
        AudioInputType.values().forEach { inputType ->
            val isSelected = settings.selectedInputType == inputType
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) Color(0xFF0C2433) else CarSurfaceVariant)
                    .border(1.dp, if (isSelected) CarCyan else CarCardBorder, RoundedCornerShape(10.dp))
                    .clickable { onSelectInput(inputType) }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .testTag("device_${inputType.name}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (inputType) {
                            AudioInputType.DEFAULT_MIC -> Icons.Default.Mic
                            AudioInputType.EXTERNAL_JACK -> Icons.Default.Headphones
                            AudioInputType.USB_AUDIO -> Icons.Default.Usb
                            AudioInputType.BLUETOOTH_SCO -> Icons.Default.BluetoothAudio
                        },
                        contentDescription = null,
                        tint = if (isSelected) CarCyan else CarTextSecondary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = inputType.displayName,
                        color = if (isSelected) CarCyan else CarTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                RadioButton(
                    selected = isSelected,
                    onClick = { onSelectInput(inputType) },
                    colors = RadioButtonDefaults.colors(
                        selectedColor = CarCyan,
                        unselectedColor = CarTextMuted
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "АППАРАТНЫЕ ЭФФЕКТЫ ЧИПА (ANDROID AUDIOFX)",
            color = CarTextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )

        // Hardware AEC
        CarSwitchCard(
            title = "Acoustic Echo Canceler (AEC)",
            subtitle = "Подавление эха от автомобильных динамиков в салоне",
            checked = settings.hardwareAecEnabled,
            onCheckedChange = onHardwareAecToggle,
            badgeText = if (stats.hardwareAecAvailable) "Чип ОК" else "Не поддерживается",
            badgeColor = if (stats.hardwareAecAvailable) CarGreen else CarAmber,
            testTag = "hw_aec_toggle"
        )

        // Hardware NS
        CarSwitchCard(
            title = "Noise Suppressor (Аппаратный NS)",
            subtitle = "Встроенное аппаратное шумоподавление звукового процессора",
            checked = settings.hardwareNsEnabled,
            onCheckedChange = onHardwareNsToggle,
            badgeText = if (stats.hardwareNsAvailable) "Чип ОК" else "Не поддерживается",
            badgeColor = if (stats.hardwareNsAvailable) CarGreen else CarAmber,
            testTag = "hw_ns_toggle"
        )

        // Hardware AGC
        CarSwitchCard(
            title = "Automatic Gain Control (AGC)",
            subtitle = "Аппаратное выравнивание громкости голоса",
            checked = settings.hardwareAgcEnabled,
            onCheckedChange = onHardwareAgcToggle,
            badgeText = if (stats.hardwareAgcAvailable) "Чип ОК" else "Не поддерживается",
            badgeColor = if (stats.hardwareAgcAvailable) CarGreen else CarAmber,
            testTag = "hw_agc_toggle"
        )

        // Software Fallback NS
        CarSwitchCard(
            title = "Программный Spectral Subtraction (Шумодав)",
            subtitle = "Собственный программный алгоритм (работает на всех чипах)",
            checked = settings.softwareNsEnabled,
            onCheckedChange = onSoftwareNsToggle,
            badgeText = "Software DSP",
            testTag = "sw_ns_toggle"
        )

        AnimatedVisibility(visible = settings.softwareNsEnabled) {
            CarSlider(
                label = "Агрессивность софтверного шумоподавления",
                value = settings.softwareNsStrength * 100f,
                valueRange = 10f..100f,
                unit = "%",
                onValueChange = { onSoftwareNsStrengthChange(it / 100f) },
                testTag = "sw_ns_strength_slider"
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "СИСТЕМНЫЕ НАСТРОЙКИ АВТОМАГНИТОЛЫ",
            color = CarTextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )

        // Auto-start on boot (BOOT_COMPLETED)
        CarSwitchCard(
            title = "Автозапуск при старте магнитолы (BOOT)",
            subtitle = "Запускать фоновую DSP службу при повороте ключа зажигания / загрузке Android",
            checked = settings.autoStartOnBoot,
            onCheckedChange = onAutoStartToggle,
            badgeText = if (settings.autoStartOnBoot) "ВКЛЮЧЕН" else "ВЫКЛ",
            badgeColor = if (settings.autoStartOnBoot) CarGreen else CarTextMuted,
            testTag = "auto_start_toggle"
        )

        // Apply last active preset on launch
        CarSwitchCard(
            title = "Применять последний пресет при запуске",
            subtitle = "Автоматически восстанавливать активный пресет («${settings.activePresetName}») при старте приложения",
            checked = settings.applyLastPresetOnLaunch,
            onCheckedChange = onApplyLastPresetToggle,
            badgeText = if (settings.applyLastPresetOnLaunch) "АКТИВЕН" else "СБРОС К CITY",
            badgeColor = if (settings.applyLastPresetOnLaunch) CarCyan else CarTextMuted,
            testTag = "apply_last_preset_toggle"
        )

        // Preset persistence status info
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0F1B2D))
                .border(1.dp, CarCyan.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Save,
                contentDescription = null,
                tint = CarCyan,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "Текущий сохраненный пресет: ${settings.activePresetName}",
                    color = CarTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (settings.applyLastPresetOnLaunch) {
                        "Все настройки эквалайзера, гейта и лимитера сохраняются в DataStore и применяются при каждом запуске."
                    } else {
                        "При каждом запуске будет загружаться стандартный пресет «Город / Шумная дорога»."
                    },
                    color = CarTextSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

/**
 * Permission Banner
 */
@Composable
private fun CarPermissionBanner(onRequestPermission: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CarAmber.copy(alpha = 0.2f))
            .border(1.dp, CarAmber)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = CarAmber,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Требуется разрешение на запись аудио для работы DSP",
                color = CarTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(containerColor = CarAmber, contentColor = Color.Black),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(text = "РАЗРЕШИТЬ", fontWeight = FontWeight.Black)
        }
    }
}
