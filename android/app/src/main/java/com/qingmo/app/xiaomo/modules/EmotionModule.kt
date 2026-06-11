package com.qingmo.app.xiaomo.modules

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingmo.app.data.model.DramaHighlight
import com.qingmo.app.ui.theme.Border
import com.qingmo.app.ui.theme.OnSurface
import com.qingmo.app.ui.theme.Primary
import com.qingmo.app.ui.theme.SurfaceVariant
import com.qingmo.app.xiaomo.InteractionModule
import com.qingmo.app.xiaomo.InteractionResult
import com.qingmo.app.xiaomo.XiaoMoCore
import com.qingmo.app.xiaomo.XiaoMoEmotion
import com.qingmo.app.xiaomo.XiaoMoPose
import kotlinx.coroutines.delay

/**
 * 情绪弹幕模块 — 高光时刻的情绪互动
 *
 * 匹配 widget_type == "emotion"
 * 渲染情绪按钮组，点击后触发粒子特效 + 小墨情绪联动
 */
class EmotionModule : InteractionModule {
    override val moduleId = "emotion"
    override val moduleName = "情绪弹幕"
    override val priority = 10

    override fun canHandle(highlight: DramaHighlight): Boolean =
        highlight.interactionType == "emotion"

    @Composable
    override fun RenderInteraction(
        highlight: DramaHighlight,
        onInteract: (InteractionResult) -> Unit,
        onDismiss: () -> Unit,
    ) {
        val emotionList = highlight.emotionHints
            ?: listOf("\uD83D\uDC4D", "\uD83D\uDC96", "\uD83D\uDE02", "\uD83D\uDE2E", "\uD83D\uDC4F")

        var selected by remember { mutableStateOf<String?>(null) }
        var showParticles by remember { mutableStateOf(false) }

        // 15s 未操作自动关闭
        LaunchedEffect(Unit) {
            delay(15_000L)
            if (selected == null) {
                onDismiss()
            }
        }

        // 选中后 1.5s 自动提交并消失
        LaunchedEffect(selected) {
            if (selected != null) {
                showParticles = true
                delay(1500L)
                val emotion = selected ?: return@LaunchedEffect
                onInteract(
                    InteractionResult(
                        moduleId = moduleId,
                        highlightId = highlight.id,
                        data = mapOf("emotion" to emotion, "highlight_type" to highlight.highlightType),
                    ),
                )
                onDismiss()
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = highlight.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = OnSurface,
                textAlign = TextAlign.Center,
            )

            // 情绪按钮组
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                emotionList.forEach { emoji ->
                    val isSelected = selected == emoji
                    val scale by animateFloatAsState(
                        targetValue = if (isSelected) 1.3f else 1f,
                        animationSpec = tween(300),
                        label = "emoji_scale",
                    )
                    val bgAlpha by animateFloatAsState(
                        targetValue = if (isSelected) 0.3f else 0.1f,
                        animationSpec = tween(300),
                        label = "emoji_bg",
                    )

                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Primary.copy(alpha = bgAlpha))
                            .clickable(enabled = selected == null) { selected = emoji },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = emoji,
                            fontSize = 24.sp,
                            modifier = Modifier.alpha(if (selected != null && !isSelected) 0.3f else 1f),
                        )
                    }
                }
            }

            // 粒子特效提示
            AnimatedVisibility(
                visible = showParticles,
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(500)),
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Text(
                    text = "${selected ?: ""} ✨ 感谢你的互动",
                    fontSize = 13.sp,
                    color = Primary,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }

    override fun processResult(result: InteractionResult) {
        // 情绪联动：根据用户选择的情绪更新小墨表情
        val emotionStr = result.data["emotion"] as? String ?: return
        val mapped = when {
            emotionStr.contains("\uD83D\uDC4D") -> XiaoMoEmotion.Excited
            emotionStr.contains("\uD83D\uDC96") -> XiaoMoEmotion.Shy
            emotionStr.contains("\uD83D\uDE02") -> XiaoMoEmotion.Laugh
            emotionStr.contains("\uD83D\uDE2E") -> XiaoMoEmotion.Surprised
            emotionStr.contains("\uD83D\uDC4F") -> XiaoMoEmotion.Worship
            else -> XiaoMoEmotion.Neutral
        }
        XiaoMoCore.setEmotion(mapped)
        XiaoMoCore.setPose(XiaoMoPose.Playing)
    }
}
