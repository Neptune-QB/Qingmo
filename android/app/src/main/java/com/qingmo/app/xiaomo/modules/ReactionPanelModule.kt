package com.qingmo.app.xiaomo.modules

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.qingmo.app.xiaomo.InteractionModule
import com.qingmo.app.xiaomo.InteractionResult

/** reaction_panel 互动组件 — 纯代码绘制，无 PNG */
class ReactionPanelModule : InteractionModule {
    override val moduleId = "reaction_panel"
    override val moduleName = "反应面板"
    override val priority = 40

    override fun onRegister() {}
    override fun onUnregister() {}

    override fun canHandle(highlight: DramaHighlight): Boolean =
        highlight.interactionType == "reaction_panel"

    @Composable
    override fun RenderInteraction(
        highlight: DramaHighlight,
        onInteract: (InteractionResult) -> Unit,
        onDismiss: () -> Unit,
    ) {
        val cfg = highlight.interactionConfig
        val title = (cfg["title"] as? String) ?: "看到这里你是什么反应？"
        val rawOptions = cfg["options"] as? List<*> ?: emptyList<Any>()
        val options = rawOptions.mapNotNull { opt ->
            when (opt) {
                is Map<*, *> -> {
                    val key = opt["key"]?.toString() ?: ""
                    val label = opt["label"]?.toString() ?: ""
                    if (key.isNotEmpty() && label.isNotEmpty()) Pair(key, label) else null
                }
                is String -> Pair(opt, opt)
                else -> null
            }
        }

        var selectedKey by remember { mutableStateOf<String?>(null) }
        var showResult by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 标题
            Text(
                text = title,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 10.dp),
                textAlign = TextAlign.Center,
            )

            // 选项网格 — 代码绘制 Chip，无 PNG
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for ((key, label) in options.take(3)) {
                    val isSelected = selectedKey == key
                    val bgColor by animateColorAsState(
                        targetValue = if (isSelected) Color.White.copy(alpha = 0.25f)
                                     else Color.White.copy(alpha = 0.08f),
                        animationSpec = tween(200),
                    )
                    val borderColor by animateColorAsState(
                        targetValue = if (isSelected) Color.White.copy(alpha = 0.6f)
                                     else Color.White.copy(alpha = 0.2f),
                        animationSpec = tween(200),
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
                            .background(bgColor)
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (isSelected && showResult) "✓ $label" else label,
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                        )
                    }
                }
            }

            // 统计条（选中后显示 — 占位，后续接入后端统计）
            if (showResult && selectedKey != null) {
                val percent = remember { (40..75).random() }
                val barWidth by animateFloatAsState(percent / 100f, tween(600))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.15f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(barWidth)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFF4CAF50)),
                        )
                    }
                    Text(
                        text = "$percent% 的观众选择了这个",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }

    override fun processResult(result: InteractionResult) {}
}
