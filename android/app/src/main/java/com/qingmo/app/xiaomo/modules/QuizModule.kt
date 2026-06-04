package com.qingmo.app.xiaomo.modules

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingmo.app.data.model.HighlightItem
import com.qingmo.app.ui.theme.Border
import com.qingmo.app.ui.theme.OnPrimary
import com.qingmo.app.ui.theme.OnSurface
import com.qingmo.app.ui.theme.OnSurfaceMuted
import com.qingmo.app.ui.theme.Primary
import com.qingmo.app.ui.theme.SurfaceVariant
import com.qingmo.app.xiaomo.InteractionModule
import com.qingmo.app.xiaomo.InteractionResult
import com.qingmo.app.xiaomo.XiaoMoCore
import com.qingmo.app.xiaomo.XiaoMoEmotion
import com.qingmo.app.xiaomo.XiaoMoPose
import kotlinx.coroutines.delay

/**
 * AI 剧情问答模块 — 高光时刻的剧情问题互动
 *
 * 匹配 widget_type == "quiz"
 * 展示剧情相关问题，用户输入答案后获得 AI 评价
 */
class QuizModule : InteractionModule {
    override val moduleId = "quiz"
    override val moduleName = "剧情问答"
    override val priority = 30

    override fun canHandle(highlight: HighlightItem): Boolean =
        highlight.widgetType == "quiz"

    @Composable
    override fun RenderInteraction(
        highlight: HighlightItem,
        onInteract: (InteractionResult) -> Unit,
        onDismiss: () -> Unit,
    ) {
        val question = highlight.title
        val hint = highlight.options?.firstOrNull() ?: ""
        var answer by remember { mutableStateOf("") }
        var submitted by remember { mutableStateOf(false) }
        var feedback by remember { mutableStateOf<String?>(null) }

        // 15s 超时自动关闭
        LaunchedEffect(Unit) {
            delay(15_000L)
            if (!submitted) {
                onDismiss()
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 问题展示
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceVariant.copy(alpha = 0.5f))
                    .padding(12.dp),
            ) {
                Column {
                    Text(
                        text = "🤔 小墨考考你",
                        fontSize = 13.sp,
                        color = Primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = question,
                        fontSize = 15.sp,
                        color = OnSurface,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 20.sp,
                    )
                    if (hint.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "💡 $hint",
                            fontSize = 12.sp,
                            color = OnSurfaceMuted,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (!submitted) {
                // 答案输入区
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Border.copy(alpha = 0.15f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    BasicTextField(
                        value = answer,
                        onValueChange = { answer = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(
                            color = OnSurface,
                            fontSize = 14.sp,
                        ),
                        cursorBrush = SolidColor(Primary),
                        decorationBox = { innerTextField ->
                            if (answer.isEmpty()) {
                                Text(
                                    text = "输入你的答案...",
                                    color = OnSurfaceMuted,
                                    fontSize = 14.sp,
                                )
                            }
                            innerTextField()
                        },
                    )
                }

                Spacer(Modifier.height(12.dp))

                // 提交按钮
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (answer.isNotBlank()) Primary else Border)
                        .clickable(enabled = answer.isNotBlank()) {
                            submitted = true
                            // 生成简单评价
                            feedback = generateFeedback(answer, question)
                            // 上报互动
                            onInteract(
                                InteractionResult(
                                    moduleId = moduleId,
                                    highlightId = highlight.id,
                                    data = mapOf(
                                        "question" to question,
                                        "answer" to answer,
                                        "highlight_type" to highlight.type,
                                    ),
                                ),
                            )
                        }
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "提交答案",
                        fontSize = 14.sp,
                        color = if (answer.isNotBlank()) OnPrimary else OnSurfaceMuted,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            // 评价反馈
            AnimatedVisibility(
                visible = feedback != null,
                enter = fadeIn(),
            ) {
                feedback?.let { fb ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Primary.copy(alpha = 0.1f))
                            .padding(12.dp),
                    ) {
                        Column {
                            Text(
                                text = "小墨点评：",
                                fontSize = 13.sp,
                                color = Primary,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = fb,
                                fontSize = 13.sp,
                                color = OnSurface,
                                lineHeight = 18.sp,
                                textAlign = TextAlign.Start,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun processResult(result: InteractionResult) {
        XiaoMoCore.setEmotion(XiaoMoEmotion.Excited)
        XiaoMoCore.setPose(XiaoMoPose.Playing)
    }

    /**
     * V1.0 简易评价：基于答案长度和关键词
     */
    private fun generateFeedback(answer: String, question: String): String {
        val len = answer.trim().length
        return when {
            len == 0 -> "唔...你好像没有输入答案呢~"
            len < 3 -> "再想想？小墨相信你能给出更棒的回答！✨"
            len < 10 -> "有想法！不过小墨想知道更多细节呢~"
            len < 20 -> "回答得不错哦！看来你对剧情有自己的理解 👍"
            else -> "好详细的回答！小墨觉得你对这部剧是真的用心了 ❤️\n继续追剧，后面还有更多精彩哦~"
        }
    }
}
