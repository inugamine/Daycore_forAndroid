package com.inugamine.daycore.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inugamine.daycore.ui.theme.*

/**
 * Speed / Pitch 用カスタムスライダー
 */
@Composable
fun DaycoreSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    displayFormat: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = DaycoreSurface,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = DaycoreTextSecondary
                )
                Text(
                    text = displayFormat.format(value),
                    style = MaterialTheme.typography.labelMedium,
                    color = DaycoreAccentLight
                )
            }

            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                colors = SliderDefaults.colors(
                    thumbColor = DaycoreAccent,
                    activeTrackColor = DaycoreAccent,
                    inactiveTrackColor = DaycoreSurfaceLight
                )
            )
        }
    }
}
