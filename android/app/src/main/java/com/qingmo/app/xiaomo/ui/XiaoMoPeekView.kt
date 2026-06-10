package com.qingmo.app.xiaomo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingmo.app.xiaomo.StableXiaomoGifByResId
import com.qingmo.app.xiaomo.XiaoMoEmotion
import com.qingmo.app.xiaomo.getXiaomoGifResId
import kotlin.math.roundToInt

private val CONTAINER_SIZE = 112.dp

@Composable
fun XiaoMoPeekView(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emotion: XiaoMoEmotion = XiaoMoEmotion.Neutral,
    gifCode: String = "idle",
) {
    var isPressed by remember { mutableStateOf(false) }
    val bubbleAlpha by animateFloatAsState(if (emotion.emoji.isNotEmpty()) 1f else 0f, tween(300))
    val bubbleY by animateFloatAsState(if (emotion.emoji.isNotEmpty()) 0f else 12f, tween(1200))
    val pressScale by animateFloatAsState(if (isPressed) 0.92f else 1f, tween(80))

    val displayCode = gifCode.ifEmpty { "idle" }
    val displayResId = remember(displayCode) { getXiaomoGifResId(displayCode) }

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(400)),
        exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)),
        modifier = modifier,
    ) {
        Box(modifier = Modifier.size(CONTAINER_SIZE), contentAlignment = Alignment.Center) {
            FloatWrapper {
                Box(
                    modifier = Modifier
                        .size(CONTAINER_SIZE).scale(pressScale)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { isPressed = true; onClick(); isPressed = false },
                    contentAlignment = Alignment.Center,
                ) {
                    StableXiaomoGifByResId(
                        resId = displayResId,
                        code = displayCode,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            // 情绪气泡
            if (emotion.emoji.isNotEmpty()) {
                Text(emotion.emoji, fontSize = 13.sp, color = Color.White,
                    modifier = Modifier.offset(y = (-28).dp - bubbleY.dp).alpha(bubbleAlpha)
                        .background(Color(0xFF222222).copy(alpha = 0.85f), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 5.dp))
            }
        }
    }
}

@Composable
private fun FloatWrapper(content: @Composable () -> Unit) {
    val transition = rememberInfiniteTransition(label = "float")
    val offset by transition.animateFloat(0f, 6f, infiniteRepeatable(tween(2500), RepeatMode.Reverse), label = "fy")
    Box(Modifier.offset { IntOffset(0, offset.roundToInt()) }, contentAlignment = Alignment.Center) { content() }
}
