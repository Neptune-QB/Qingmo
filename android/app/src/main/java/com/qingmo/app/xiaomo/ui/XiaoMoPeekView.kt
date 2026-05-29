package com.qingmo.app.xiaomo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.qingmo.app.R
import com.qingmo.app.ui.theme.Background
import com.qingmo.app.ui.theme.Border
import kotlin.math.roundToInt

/**
 * 小墨 Peek 状态 — 半挂在屏幕右侧边缘，仅露出头部和手
 *
 * 尺寸：60dp × 80dp
 * 入场：右侧滑入 + 弹簧弹跳 400ms
 * 呼吸动画：轻微上下浮动
 * 交互：点击 → Expanded 状态
 */
@Composable
fun XiaoMoPeekView(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = tween(durationMillis = 400),
        ),
        exit = slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(durationMillis = 300),
        ),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .size(60.dp, 80.dp)
                .shadow(4.dp, RoundedCornerShape(12.dp)),
        ) {
            FloatWrapper {
                Image(
                    painter = painterResource(id = R.drawable.xiaomo_peek),
                    contentDescription = "小墨",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(60.dp, 80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Background, RoundedCornerShape(12.dp))
                        .clickable { onClick() },
                )
            }
        }
    }
}

/**
 * 呼吸动画包装：上下轻微浮动（幅度 ±3dp，周期 2500ms）
 */
@Composable
private fun FloatWrapper(content: @Composable () -> Unit) {
    val transition = rememberInfiniteTransition(label = "xiaomo_float")
    val floatOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2500),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "xiaomo_float_offset",
    )

    Box(
        modifier = Modifier.offset { IntOffset(0, floatOffset.roundToInt()) },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
