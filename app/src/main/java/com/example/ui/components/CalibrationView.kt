package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CalibrationResult
import com.example.model.CalibrationState
import com.example.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun CalibrationTabContent(
    calibrationState: CalibrationState,
    onStartCalibration: () -> Unit,
    onCancelCalibration: () -> Unit,
    onApplyResult: (CalibrationResult) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("calibration_tab_content")
    ) {
        when (calibrationState) {
            is CalibrationState.Idle -> {
                CalibrationIdleView(onStartCalibration = onStartCalibration)
            }
            is CalibrationState.InProgress -> {
                CalibrationInProgressView(
                    state = calibrationState,
                    onCancel = onCancelCalibration
                )
            }
            is CalibrationState.Completed -> {
                CalibrationCompletedView(
                    result = calibrationState.result,
                    onApply = { onApplyResult(calibrationState.result) },
                    onRecalibrate = onStartCalibration,
                    onDismiss = onCancelCalibration
                )
            }
            is CalibrationState.Error -> {
                CalibrationErrorView(
                    message = calibrationState.message,
                    onRetry = onStartCalibration
                )
            }
        }
    }
}

/**
 * 1. Idle screen explaining the 10-second calibration procedure
 */
@Composable
private fun CalibrationIdleView(onStartCalibration: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "ИНТЕЛЛЕКТУАЛЬНАЯ АВТОКАЛИБРОВКА (10 СЕК)",
                    color = CarCyan,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "Точная настройка под акустику вашего салона, скорость и шум кондиционера",
                    color = CarTextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        // Instructions Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CarSurfaceVariant),
            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(CarCardBorder, CarCardBorder))),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "КАК ЭТО РАБОТАЕТ:",
                    color = CarTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                InstructionItem(
                    number = "1",
                    title = "Замер фонового шума салона",
                    description = "Аудиопроцессор в течение 10 секунд непрерывно анализирует частотный спектр и уровень децибел шума шин, мотора и ветра."
                )

                InstructionItem(
                    number = "2",
                    title = "Вычисление порога Noise Gate",
                    description = "Порог шумового гейта будет автоматически выставлен на +4.5 дБ выше пиков фонового шума, чтобы полностью исключить шум в паузах."
                )

                InstructionItem(
                    number = "3",
                    title = "Оптимизация Spectral NS и Low-Cut",
                    description = "Подбирается сила программного подавления и частота среза инфразвукового гула подвески (High-Pass фильтр)."
                )
            }
        }

        // Tips Banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0F1E32))
                .border(1.dp, CarCyan.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = CarCyan,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Совет: запустите калибровку во время движения с привычной скоростью и включенным климат-контролем, соблюдая тишину в салоне.",
                color = CarTextPrimary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }

        // Big Start Button
        Button(
            onClick = onStartCalibration,
            colors = ButtonDefaults.buttonColors(
                containerColor = CarCyan,
                contentColor = Color.Black
            ),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(vertical = 14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("start_calibration_button")
        ) {
            Icon(
                imageVector = Icons.Default.Sensors,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "НАЧАТЬ АВТОКАЛИБРОВКУ (10 СЕКУНД)",
                fontSize = 15.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

/**
 * 2. In-Progress countdown and acoustic radar screen
 */
@Composable
private fun CalibrationInProgressView(
    state: CalibrationState.InProgress,
    onCancel: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "ИДЁТ АКУСТИЧЕСКИЙ ЗАМЕР САЛОНА",
                color = CarCyan,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Пожалуйста, сохраняйте тишину — микрофон замеряет фоновый шум",
                color = CarTextSecondary,
                fontSize = 13.sp
            )
        }

        // Central Radar / Countdown Display
        Box(
            modifier = Modifier.size(180.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension / 2f - 10f

                // Outer pulsing wave
                drawCircle(
                    color = CarCyan.copy(alpha = 0.15f),
                    radius = radius * pulseScale,
                    center = center
                )

                // Background track
                drawCircle(
                    color = Color(0xFF131C2E),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 12f)
                )

                // Progress sweep arc
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(CarCyan, CarAmber, CarCyan)
                    ),
                    startAngle = -90f,
                    sweepAngle = state.progress * 360f,
                    useCenter = false,
                    style = Stroke(width = 12f, cap = StrokeCap.Round)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${state.remainingSeconds}",
                    color = CarTextPrimary,
                    fontSize = 46.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "СЕКУНД",
                    color = CarCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Live noise statistics bar
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(10.dp))
                .background(CarSurfaceVariant)
                .border(1.dp, CarCardBorder, RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "ТЕКУЩИЙ RMS", color = CarTextSecondary, fontSize = 11.sp)
                    val dbText = if (state.currentNoiseRmsDb <= -90f) "-- дБ" else "${(state.currentNoiseRmsDb * 10).roundToInt() / 10f} дБ"
                    Text(
                        text = dbText,
                        color = CarCyan,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "ПИКОВЫЙ ШУМ", color = CarTextSecondary, fontSize = 11.sp)
                    val peakText = if (state.currentNoisePeakDb <= -90f) "-- дБ" else "${(state.currentNoisePeakDb * 10).roundToInt() / 10f} дБ"
                    Text(
                        text = peakText,
                        color = CarAmber,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "СЭМПЛОВ", color = CarTextSecondary, fontSize = 11.sp)
                    Text(
                        text = "${state.samplesCollected}",
                        color = CarTextPrimary,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Cancel Button
        OutlinedButton(
            onClick = onCancel,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = CarTextSecondary),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .height(44.dp)
                .width(160.dp)
                .testTag("cancel_calibration_button")
        ) {
            Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "ОТМЕНА", fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * 3. Completed screen with measured environment and suggested values
 */
@Composable
private fun CalibrationCompletedView(
    result: CalibrationResult,
    onApply: () -> Unit,
    onRecalibrate: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Result Header Banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF063321))
                .border(1.dp, CarGreen, RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = CarGreen,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "ЗАМЕР ЗАВЕРШЁН: РЕЗУЛЬТАТЫ ГОТОВЫ",
                    color = CarGreen,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Определено: ${result.environmentDescription}",
                    color = CarTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Measured Data Stats Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ResultMetricCard(
                label = "Средний шум салона (RMS)",
                value = "${result.measuredNoiseRmsDb} дБFS",
                color = CarCyan,
                modifier = Modifier.weight(1f)
            )
            ResultMetricCard(
                label = "Пиковый потолок шума",
                value = "${result.measuredNoisePeakDb} дБFS",
                color = CarAmber,
                modifier = Modifier.weight(1f)
            )
        }

        // Parameter Recommendation Comparison
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CarSurfaceVariant),
            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(CarCardBorder, CarCardBorder))),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "РАССЧИТАННЫЕ ОПТИМАЛЬНЫЕ ЗНАЧЕНИЯ DSP:",
                    color = CarTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                ComparisonRow(
                    parameterName = "Шумовой гейт (Noise Gate)",
                    currentValue = "${result.previousGateThresholdDb} дБ",
                    suggestedValue = "${result.suggestedGateThresholdDb} дБ",
                    explanation = "+4.5 дБ над фоновым шумом для отсечения звука салона"
                )

                HorizontalDivider(color = CarCardBorder, thickness = 0.8.dp)

                ComparisonRow(
                    parameterName = "Программный шумоподавитель (NS)",
                    currentValue = "${(result.previousSoftwareNsStrength * 100).roundToInt()}%",
                    suggestedValue = "${(result.suggestedSoftwareNsStrength * 100).roundToInt()}%",
                    explanation = "Адаптивная сила подавления гула и шипения"
                )

                HorizontalDivider(color = CarCardBorder, thickness = 0.8.dp)

                ComparisonRow(
                    parameterName = "High-Pass Low-Cut (Срез вибраций)",
                    currentValue = "${result.previousHpfCutoffHz.roundToInt()} Гц",
                    suggestedValue = "${result.suggestedHpfCutoffHz.roundToInt()} Гц",
                    explanation = "Устранение инфразвукового шума подвески и кузова"
                )

                HorizontalDivider(color = CarCardBorder, thickness = 0.8.dp)

                ComparisonRow(
                    parameterName = "Gain Booster (Усиление микрофона)",
                    currentValue = "Базовое",
                    suggestedValue = "+${result.suggestedGainBoosterDb.roundToInt()} дБ",
                    explanation = "Оптимальная разборчивость тихой речи без перегруза"
                )
            }
        }

        // Big Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onApply,
                colors = ButtonDefaults.buttonColors(containerColor = CarCyan, contentColor = Color.Black),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1.4f)
                    .height(48.dp)
                    .testTag("apply_calibration_button")
            ) {
                Icon(imageVector = Icons.Default.Done, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "ПРИМЕНИТЬ НАСТРОЙКИ", fontWeight = FontWeight.Black)
            }

            OutlinedButton(
                onClick = onRecalibrate,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CarTextPrimary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("recalibrate_button")
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "ПОВТОРИТЬ", fontWeight = FontWeight.Bold)
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(CarSurfaceVariant)
            ) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Закрыть", tint = CarTextSecondary)
            }
        }
    }
}

@Composable
private fun ResultMetricCard(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(CarSurfaceVariant)
            .border(1.dp, CarCardBorder, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Text(text = label, color = CarTextSecondary, fontSize = 11.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            color = color,
            fontSize = 18.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun ComparisonRow(
    parameterName: String,
    currentValue: String,
    suggestedValue: String,
    explanation: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = parameterName,
                color = CarTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = currentValue,
                    color = CarTextMuted,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = CarCyan,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = suggestedValue,
                    color = CarCyan,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black
                )
            }
        }
        Text(
            text = explanation,
            color = CarTextSecondary,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun InstructionItem(
    number: String,
    title: String,
    description: String
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(CarCyan.copy(alpha = 0.2f))
                .border(1.dp, CarCyan, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                color = CarCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = CarTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                color = CarTextSecondary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun CalibrationErrorView(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = CarRed,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Ошибка калибровки",
            color = CarTextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = message,
            color = CarTextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = CarCyan, contentColor = Color.Black)
        ) {
            Text(text = "ПОПРОБОВАТЬ СНОВА", fontWeight = FontWeight.Bold)
        }
    }
}
