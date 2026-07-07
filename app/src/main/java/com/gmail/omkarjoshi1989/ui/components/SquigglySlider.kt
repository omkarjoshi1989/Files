package com.gmail.omkarjoshi1989.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * A custom slider with a squiggly (wavy) track for the active progress part,
 * mimicking the Android 13 media notification look.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SquigglySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    isPlaying: Boolean = false,
    colors: SliderColors = SliderDefaults.colors(
        activeTrackColor = Color.White,
        inactiveTrackColor = Color.White.copy(alpha = 0.3f),
        thumbColor = Color.White
    )
) {
    val interactionSource = remember { MutableInteractionSource() }
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        onValueChangeFinished = onValueChangeFinished,
        colors = colors,
        interactionSource = interactionSource,
        track = { sliderState ->
            SquigglyTrack(
                sliderState = sliderState,
                isPlaying = isPlaying,
                colors = colors
            )
        },
        thumb = {
            SliderDefaults.Thumb(
                interactionSource = interactionSource,
                colors = colors,
                thumbSize = androidx.compose.ui.unit.DpSize(4.dp, 16.dp)
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SquigglyTrack(
    sliderState: SliderState,
    isPlaying: Boolean,
    colors: SliderColors
) {
    val infiniteTransition = rememberInfiniteTransition(label = "SquigglyPhase")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val activeColor = colors.activeTrackColor
    val inactiveColor = colors.inactiveTrackColor

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        
        // Progress point
        val fraction = sliderState.value.coerceIn(0f, 1f)
        val progressX = width * fraction

        // 1. Draw Inactive Track (Straight line)
        drawLine(
            color = inactiveColor,
            start = Offset(progressX, centerY),
            end = Offset(width, centerY),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )

        // 2. Draw Active Track (Wavy line)
        if (progressX > 0) {
            val path = Path()
            val wavelength = 40.dp.toPx()
            val amplitude = if (isPlaying) 4.dp.toPx() else 0f
            val currentPhase = if (isPlaying) phase else 0f

            path.moveTo(0f, centerY)
            
            val steps = (progressX / 2.dp.toPx()).toInt().coerceAtLeast(1)
            for (i in 0..steps) {
                val x = (i.toFloat() / steps) * progressX
                val y = centerY + sin((x / wavelength) * 2 * PI.toFloat() + currentPhase) * amplitude
                path.lineTo(x, y)
            }

            drawPath(
                path = path,
                color = activeColor,
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}
