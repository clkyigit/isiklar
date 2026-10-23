package com.yigit.isiklar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yigit.isiklar.model.LightDevice
import com.yigit.isiklar.model.LightState
import com.yigit.isiklar.model.Rgb
import com.yigit.isiklar.model.kelvinToRgb
import kotlin.math.roundToInt

private val PRESETS = listOf(
    Rgb(255, 0, 0),
    Rgb(255, 100, 0),
    Rgb(255, 200, 0),
    Rgb(0, 255, 60),
    Rgb(0, 220, 255),
    Rgb(30, 60, 255),
    Rgb(150, 40, 255),
    Rgb(255, 40, 160),
)

private val WHITE_PRESETS = listOf("Sıcak" to 2700, "Doğal" to 4000, "Gün ışığı" to 6500)

fun Rgb.toColor(): Color = Color(r, g, b)

/** Kartta gösterilecek renk. */
fun LightState.displayColor(): Color {
    val k = kelvin
    val c = color
    return when {
        k != null -> kelvinToRgb(k).toColor()
        c != null -> c.toColor()
        else -> Color(0xFFFFC870)
    }
}

private fun hueOf(rgb: Rgb): Float {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(rgb.r, rgb.g, rgb.b, hsv)
    return hsv[0]
}

private fun hueToRgb(hue: Float): Rgb =
    Rgb.fromInt(android.graphics.Color.HSVToColor(floatArrayOf(hue.coerceIn(0f, 359.9f), 1f, 1f)) and 0xFFFFFF)

@Composable
fun ColorControls(
    device: LightDevice,
    onColor: (Rgb) -> Unit,
    onKelvin: (Int) -> Unit,
) {
    var hue by remember(device.id) { mutableFloatStateOf(device.state.color?.let { hueOf(it) } ?: 0f) }
    var kelvin by remember(device.id) { mutableFloatStateOf((device.state.kelvin ?: 4000).toFloat()) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PRESETS.forEach { p ->
                val selected = device.state.kelvin == null && device.state.color == p
                Swatch(p.toColor(), selected) {
                    hue = hueOf(p)
                    onColor(p)
                }
            }
        }

        Text("Renk tonu", style = MaterialTheme.typography.labelMedium)
        GradientSlider(
            value = hue,
            range = 0f..359f,
            brush = Brush.horizontalGradient(listOf(0f, 60f, 120f, 180f, 240f, 300f, 359f).map { Color.hsv(it, 1f, 1f) }),
            onChange = { hue = it },
            onFinished = { onColor(hueToRgb(hue)) },
        )

        Text("Beyaz ton · ${kelvin.roundToInt()} K", style = MaterialTheme.typography.labelMedium)
        GradientSlider(
            value = kelvin,
            range = 2000f..9000f,
            brush = Brush.horizontalGradient(listOf(2000, 3500, 5000, 6500, 9000).map { kelvinToRgb(it).toColor() }),
            onChange = { kelvin = it },
            onFinished = { onKelvin(kelvin.roundToInt()) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WHITE_PRESETS.forEach { (label, k) ->
                OutlinedButton(onClick = {
                    kelvin = k.toFloat()
                    onKelvin(k)
                }) { Text(label) }
            }
        }
    }
}

@Composable
private fun Swatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
    )
}

@Composable
private fun GradientSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    brush: Brush,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit,
) {
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(brush),
        )
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onFinished,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
    }
}
