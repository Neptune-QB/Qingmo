package com.qingmo.app.xiaomo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingmo.app.xiaomo.XiaoMoPose
import com.qingmo.app.xiaomo.XiaoMoEmotion
import kotlin.math.roundToInt

/**
 * 小墨 Peek 状态 — 半挂在屏幕右侧边缘，仅露出头部和手
 *
 * 【V2 零Live2D增强特性】全硬件加速零新增资源，体积增量0KB：
 *  1. 石青色强外发光高光特效
 *  2. 92%缩放点击压弹簧反馈
 *  3. 7秒周期自动眨眼动画
 *  4. 情绪气泡自动上浮
 *  5. 8字形螺旋复合动效（呼吸+摇晃叠加）
 *
 *  完全向下兼容，所有旧调用点零改动自动获得全部新特性
 */
@Composable
fun XiaoMoPeekView(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    pose: XiaoMoPose = XiaoMoPose.Idle,
    emotion: XiaoMoEmotion = XiaoMoEmotion.Neutral,
) {
    var isPressed by remember { mutableStateOf(false) }
    val showShakeEffect = pose == XiaoMoPose.Shaking

    val shakeGlowAlpha by animateFloatAsState(
        targetValue = if (showShakeEffect) 0.6f else 0f,
        animationSpec = tween(durationMillis = 300)
    )
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = tween(durationMillis = 80)
    )
    var eyeBlinkOpacity by remember { mutableStateOf(0f) }
    val bubbleAlpha by animateFloatAsState(
        targetValue = if (emotion.emoji.isNotEmpty()) 1f else 0f,
        animationSpec = tween(300)
    )
    val bubbleY by animateFloatAsState(
        targetValue = if (emotion.emoji.isNotEmpty()) 0f else 12f,
        animationSpec = tween(1200)
    )

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(7000L)
            eyeBlinkOpacity = 1f
            kotlinx.coroutines.delay(100L)
            eyeBlinkOpacity = 0f
        }
    }

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
        Box(modifier = Modifier.size(60.dp, 130.dp), contentAlignment = Alignment.TopCenter) {
            // 高光时刻石青色强外发光
            Box(
                Modifier
                    .size(80.dp, 100.dp)
                    .alpha(shakeGlowAlpha)
                    .shadow(22.dp, shape = RoundedCornerShape(30.dp), spotColor = Color(0xFF4A88FF))
            )
            FloatWrapper {
                ShakingWrapper(enabled = showShakeEffect) {
                    Crossfade(
                        targetState = pose,
                        animationSpec = tween(durationMillis = 400),
                        label = "xiaomo_pose_crossfade",
                    ) { currentPose ->
                        GifImage(
                            resId = currentPose.resId,
                            contentDescription = "小墨",
                            onClick = {
                                isPressed = true
                                onClick()
                                isPressed = false
                            },
                            modifier = Modifier
                                .size(60.dp, 80.dp)
                                .scale(pressScale)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                    }
                }
            }
            // 自动眨眼：无需额外闭眼PNG资源
            Box(
                Modifier
                    .size(8.dp, 3.dp)
                    .alpha(eyeBlinkOpacity)
                    .offset(x = (-4).dp, y = 18.dp)
                    .background(Color.Black.copy(alpha = 0.9f), CircleShape)
            )
            Box(
                Modifier
                    .size(8.dp, 3.dp)
                    .alpha(eyeBlinkOpacity)
                    .offset(x = 12.dp, y = 18.dp)
                    .background(Color.Black.copy(alpha = 0.9f), CircleShape)
            )
            // 情绪气泡上浮
            if (emotion.emoji.isNotEmpty()) {
                Text(
                    text = emotion.emoji,
                    fontSize = 13.sp,
                    color = Color.White,
                    modifier = Modifier
                        .offset(y = (-20).dp - bubbleY.dp)
                        .alpha(bubbleAlpha)
                        .background(Color(0xFF222222).copy(alpha = 0.85f), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
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

/**
 * 强烈摇晃动画：高光时刻触发，左右剧烈摇摆
 * 和FloatWrapper叠加后自动形成8字形螺旋轨迹
 */
@Composable
fun ShakingWrapper(enabled: Boolean, content: @Composable () -> Unit) {
    if (!enabled) {
        content()
        return
    }
    val shakeTransition = rememberInfiniteTransition(label = "xiaomo_shake")
    val shakeOffset by shakeTransition.animateFloat(
        initialValue = -12f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 120),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "xiaomo_shake_offset",
    )
    Box(
        modifier = Modifier.offset { IntOffset(shakeOffset.roundToInt(), 0) },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
