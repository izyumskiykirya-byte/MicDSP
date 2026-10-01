package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlin.math.roundToInt

/**
 * High-visibility automotive LED Segment VU-Meter for in-vehicle head units.
 * Renders level from -60 dBFS to 0 dBFS with Green / Yellow / Orange / Red LED segments.
 */
@Composable
fun AutomotiveVuMeter(
    label: String,
    rmsDb: Float,
    peakDb: Float,
    modifier: Modifier = Modifier,
    isClipping: Boolean = false
) {
    // Normalize dB (-60 to 0) to [0.0 .. 1.0]
    val normRms = ((rmsDb + 60.0f) / 60.0f).coerceIn(0.0f, 1.0f)
    val normPeak = ((peakDb + 60.0f) / 60.0f).coerceIn(0.0f, 1.0f)

    val animatedRms by animateFloatAsState(
        targetValue = normRms,
        animationSpec = tween(durationMillis = 60),
        label = "rms_anim"
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(CarSurfaceVariant)
            .border(1.dp, CarCardBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("vu_meter_$label")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = CarTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            val dbText = if (rmsDb <= -59.5f) "-∞ dB" else "${(rmsDb * 10).roundToInt() / 10f} dB"
            Text(
                text = dbText,
                color = if (isClipping) CarRedBright else CarCyan,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Horizontal Segmented LED Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF070B12))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val totalWidth = size.width
                val totalHeight = size.height
                val segmentCount = 28
                val segmentGap = 2f
                val segmentWidth = (totalWidth - (segmentCount - 1) * segmentGap) / segmentCount

                for (i in 0 until segmentCount) {
                    val segmentFraction = (i + 1).toFloat() / segmentCount
                    val isLit = segmentFraction <= animatedRms
                    val isPeakLit = (segmentFraction - (1f / segmentCount) <= normPeak && normPeak <= segmentFraction)

                    // Color assignment based on dB zone
                    // 0..65%: Green (-60 to -20 dB)
                    // 65..82%: Yellow (-20 to -10 dB)
                    // 82..94%: Orange (-10 to -3 dB)
                    // >94%: Red (-3 to 0 dB)
                    val activeColor = when {
                        segmentFraction > 0.94f -> CarVuRed
                        segmentFraction > 0.82f -> CarVuOrange
                        segmentFraction > 0.65f -> CarVuYellow
                        else -> CarVuGreen
                    }

                    val color = when {
                        isLit -> activeColor
                        isPeakLit -> Color.White
                        else -> activeColor.copy(alpha = 0.15f) // Dim unlit segment
                    }

                    val left = i * (segmentWidth + segmentGap)
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(left, 0f),
                        size = Size(segmentWidth, totalHeight),
                        cornerRadius = CornerRadius(2f, 2f)
                    )
                }
            }
        }
    }
}
