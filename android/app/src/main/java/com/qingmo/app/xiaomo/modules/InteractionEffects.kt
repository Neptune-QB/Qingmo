package com.qingmo.app.xiaomo.modules

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * 白色圆环扩散动画 — 纯 Canvas 绘制，无需图片
 * @param triggered 触发动画
 */
@Composable
fun ClickRingEffect(
    triggered: Boolean,
    onFinished: () -> Unit = {},
) {
    val radius = remember { Animatable(10f) }
    val alpha by animateFloatAsState(if (triggered) 1f else 0f, tween(200))

    LaunchedEffect(triggered) {
        if (triggered) {
            radius.snapTo(10f)
            radius.animateTo(80f, tween(400))
            onFinished()
        }
    }

    if (alpha > 0.01f) {
        Canvas(modifier = Modifier.size(180.dp).alpha(alpha)) {
            drawCircle(
                color = Color.White.copy(alpha = 0.4f),
                radius = radius.value,
                center = Offset(size.width / 2, size.height / 2),
                style = Stroke(width = 2.5f),
            )
        }
    }
}

/**
 * "+1" 上浮淡出动画 — 纯 Text 实现，无需图片
 * @param text 显示文字
 * @param triggered 触发动画
 */
@Composable
fun PlusOneEffect(
    text: String = "+1",
    triggered: Boolean,
    onFinished: () -> Unit = {},
) {
    val offsetY = remember { Animatable(0f) }
    val alpha by animateFloatAsState(if (triggered) 1f else 0f, tween(200))

    LaunchedEffect(triggered) {
        if (triggered) {
            offsetY.snapTo(0f)
            offsetY.animateTo(-60f, tween(800))
            onFinished()
        }
    }

    if (alpha > 0.01f) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = Color(0xFF4CAF50),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .alpha(alpha * (1f - offsetY.value / 80f).coerceIn(0f, 1f))
                    .offset { IntOffset(0, offsetY.value.toInt()) },
            )
        }
    }
}
