package com.example.courseschedule.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.materials.HazeMaterials

/** 全局共享的毛玻璃状态（由 MainActivity 提供） */
val LocalHazeState = compositionLocalOf<HazeState> { error("HazeState 未提供") }

/**
 * 真·毛玻璃：把背后内容模糊后透出来。
 * 需要配合 MainActivity 里的 hazeSource 背景源使用。
 */
fun Modifier.frostedGlass(corner: Dp = 16.dp): Modifier = composed {
    val state = LocalHazeState.current
    val shape = RoundedCornerShape(corner)
    this
        .clip(shape)
        .hazeBlur(
            input = HazeInput.Sources(state),
            style = HazeMaterials.thin(),
        )
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
