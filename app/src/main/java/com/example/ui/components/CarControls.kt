package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlin.math.roundToInt

/**
 * Car-friendly slider with large touch area and clear numerical indicator
 */
@Composable
fun CarSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    unit: String = "",
    step: Float = 0f,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "car_slider",
    enabled: Boolean = true
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CarSurfaceVariant)
            .border(1.dp, CarCardBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = if (enabled) CarTextPrimary else CarTextMuted,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            val displayValue = "${(value * 10).roundToInt() / 10f} $unit"
            Text(
                text = displayValue,
                color = if (enabled) CarCyan else CarTextMuted,
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = CarCyan,
                activeTrackColor = CarCyan,
                inactiveTrackColor = Color(0xFF0F172A),
                disabledThumbColor = CarTextMuted,
                disabledActiveTrackColor = CarTextMuted
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp) // Minimum automotive touch target
        )
    }
}

/**
 * Big tactile switch card for driver comfort
 */
@Composable
fun CarSwitchCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    badgeText: String? = null,
    badgeColor: Color = CarGreen,
    testTag: String = "car_switch"
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (checked) CarSurfaceVariant else Color(0xFF0F1523))
            .border(1.dp, if (checked) CarCyan.copy(alpha = 0.5f) else CarCardBorder, RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = CarTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                if (badgeText != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(badgeColor.copy(alpha = 0.2f))
                            .border(1.dp, badgeColor, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badgeText,
                            color = badgeColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            if (subtitle.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = CarTextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = CarCyan,
                uncheckedThumbColor = CarTextSecondary,
                uncheckedTrackColor = Color(0xFF0D121D),
                uncheckedBorderColor = CarCardBorder
            ),
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

/**
 * Status indicator pill
 */
@Composable
fun StatusLedPill(
    label: String,
    isActive: Boolean,
    activeColor: Color = CarGreen,
    inactiveColor: Color = CarTextMuted,
    activeText: String = "АКТИВЕН",
    inactiveText: String = "ВЫКЛ",
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF080D17))
            .border(1.dp, if (isActive) activeColor.copy(alpha = 0.5f) else CarCardBorder, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (isActive) activeColor else inactiveColor)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$label: ${if (isActive) activeText else inactiveText}",
            color = if (isActive) CarTextPrimary else CarTextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
