package com.qingmo.app.xiaomo.modules

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingmo.app.data.model.DramaHighlight
import com.qingmo.app.ui.theme.Border
import com.qingmo.app.ui.theme.OnSurface
import com.qingmo.app.ui.theme.OnSurfaceMuted
import com.qingmo.app.ui.theme.Primary
import com.qingmo.app.ui.theme.Secondary
import com.qingmo.app.ui.theme.SurfaceVariant
import com.qingmo.app.xiaomo.InteractionModule
import com.qingmo.app.xiaomo.InteractionResult
import com.qingmo.app.xiaomo.XiaoMoCore
import com.qingmo.app.xiaomo.XiaoMoEmotion
import com.qingmo.app.xiaomo.XiaoMoPose

/**
 * 剧情投票模块 — 高光时刻的观众投票互动
 *
 * 匹配 widget_type == "vote"
 * 渲染投票问题 + 多选项，选择后展示百分比动画
 */
class VoteModule : InteractionModule {
    override val moduleId = "vote"
    override val moduleName = "剧情投票"
    override val priority = 20

    override fun canHandle(highlight: DramaHighlight): Boolean =
        highlight.interactionType == "vote"

    @Composable
    override fun RenderInteraction(
        highlight: DramaHighlight,
        onInteract: (InteractionResult) -> Unit,
        onDismiss: () -> Unit,
    ) {
        val options = highlight.options ?: listOf("选项 A", "选项 B")
        var selected by remember { mutableStateOf<Int?>(null) }
        var dismissed by remember { mutableStateOf(false) }

        // 模拟投票结果（V1.0 本地随机，V2.0 对接后端统计）
        val results = remember {
            val r = mutableListOf<Float>()
            var remaining = 1f
            for (i in options.indices) {
                val share = if (i == options.lastIndex) remaining
                else remaining * (0.3f + 0.4f * kotlin.random.Random.nextFloat())
                r.add(share)
                remaining -= share
            }
            r
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

            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                options.forEachIndexed { index, option ->
                    val isSelected = selected == index
                    val progress by animateFloatAsState(
                        targetValue = if (selected != null) results[index] else 0f,
                        animationSpec = tween(800),
                        label = "vote_progress_$index",
                    )
                    val bgColor = if (isSelected) Primary.copy(alpha = 0.2f) else SurfaceVariant.copy(alpha = 0.5f)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(bgColor)
                            .clickable(enabled = selected == null && !dismissed) {
                                selected = index
                                onInteract(
                                    InteractionResult(
                                        moduleId = moduleId,
                                        highlightId = highlight.id,
                                        data = mapOf(
                                            "vote" to option,
                                            "option_index" to index,
                                            "highlight_type" to highlight.highlightType,
                                        ),
                                    ),
                                )
                            }
                            .padding(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = option,
                                fontSize = 14.sp,
                                color = if (isSelected) Primary else OnSurface,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            )
                            if (selected != null) {
                                Text(
                                    text = "${(progress * 100).toInt()}%",
                                    fontSize = 13.sp,
                                    color = if (isSelected) Primary else OnSurfaceMuted,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                        if (selected != null) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .padding(top = 6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = if (isSelected) Primary else OnSurfaceMuted.copy(alpha = 0.5f),
                                trackColor = Border.copy(alpha = 0.3f),
                            )
                        }
                    }
                }
            }
        }
    }

    override fun processResult(result: InteractionResult) {
        val emotionStr = result.data["highlight_type"] as? String ?: return
        val mapped = when (emotionStr) {
            "cliffhanger" -> XiaoMoEmotion.Surprised
            "choice_point" -> XiaoMoEmotion.Confused
            "emotional_burst" -> XiaoMoEmotion.Shy
            "power_moment" -> XiaoMoEmotion.Excited
            "reversal" -> XiaoMoEmotion.Surprised
            "slapback" -> XiaoMoEmotion.Excited
            "heartbreak" -> XiaoMoEmotion.Shy
            else -> XiaoMoEmotion.Excited
        }
        XiaoMoCore.setEmotion(mapped)
        XiaoMoCore.setPose(XiaoMoPose.Playing)
    }
}
