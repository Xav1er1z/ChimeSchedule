package com.example.courseschedule.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.materials.HazeMaterials

/**
 * 全局共享的毛玻璃状态（由 MainActivity 提供）。
 * 默认值为 null，避免在未提供时抛异常导致闪退。
 */
val LocalHazeState = compositionLocalOf<HazeState?> { null }

/**
 * 毛玻璃表面：
 * - 若背景源可用，使用真实的背景模糊；
 * - 否则退化为半透明磨砂表面，保证不会崩溃。
 */
fun Modifier.frostedGlass(corner: Dp = 16.dp): Modifier = composed {
    val state = LocalHazeState.current
    val shape = RoundedCornerShape(corner)

    if (state != null) {
        this
            .clip(shape)
            .hazeBlur(
                input = HazeInput.Sources(state),
                style = HazeMaterials.thin(),
            )
    } else {
        // 兜底：半透明磨砂 + 细描边
        val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
        val fill = if (dark) {
            Brush.verticalGradient(listOf(Color.White.copy(0.12f), Color.White.copy(0.06f)))
        } else {
            Brush.verticalGradient(listOf(Color.White.copy(0.72f), Color.White.copy(0.50f)))
        }
        this
            .clip(shape)
            .background(fill)
            .border(1.dp, Color.White.copy(0.28f), shape)
    }
}

/** 按压反馈：160ms 缩放至 0.97，无回弹 */
@Composable
fun Modifier.pressableClickable(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = 160),
        label = "press",
    )
    return this
        .scale(scale)
        .clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            enabled = enabled,
            onClick = onClick,
        )
}
