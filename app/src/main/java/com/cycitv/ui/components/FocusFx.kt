package com.cycitv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cycitv.ui.theme.CycShape
import com.cycitv.ui.theme.Primary

/**
 * TV 焦点效果: 轻微放大 + 品牌色描边。
 *
 * 圆角默认值必须与被聚焦元素的真实圆角一致 —— 原实现默认 0dp(直角描边),
 * 而调用方 ScheduleScreen 传了 12dp, 导致"描边是圆的、卡片是方的"错位。
 * 现在默认取卡片圆角, 调用方按元素类型传 chip/badge 等更小的值。
 */
@Composable
fun Modifier.tvFocus(
    scale: Float = 1.04f,
    cornerRadius: Dp = 12.dp,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()

    val s by animateFloatAsState(
        targetValue = if (isFocused) scale else 1f,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = 150,
            easing = androidx.compose.animation.core.CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f),
        ),
        label = "focus-scale",
    )
    val strokePx = with(LocalDensity.current) { 2.5f.dp.toPx() }
    val cornerPx = with(LocalDensity.current) { cornerRadius.toPx() }
    // drawWithContent 的 lambda 不是 Composable 上下文, 主题取色必须提前在此完成
    val ringColor = Primary

    // 必须从 this 接续, 不能从 Modifier 重新起链 —— 否则调用方写在 tvFocus 之前的
    // fillMaxWidth()/width()/padding() 会被整条丢弃, 元素退化成按内容包裹。
    // (2026-10-19 实测: "继续观看"卡片设了 width(286.dp) 仍只有 78dp 宽, 文字列被压成 0)
    return this
        .graphicsLayer {
            scaleX = s
            scaleY = s
        }
        .focusable(interactionSource = interaction)
        .drawWithContent {
            drawContent()
            if (isFocused) {
                drawRoundRect(
                    color = ringColor,
                    topLeft = Offset(strokePx / 2, strokePx / 2),
                    size = Size(size.width - strokePx, size.height - strokePx),
                    cornerRadius = CornerRadius(cornerPx.coerceAtLeast(0f)),
                    style = Stroke(width = strokePx),
                )
            }
        }
}

@Composable
fun Modifier.focusRing(focused: Boolean): Modifier =
    then(
        if (focused) Modifier.border(2.5f.dp, Primary, CycShape.card) else Modifier
    )

