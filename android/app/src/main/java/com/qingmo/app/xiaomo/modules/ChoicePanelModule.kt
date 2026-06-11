package com.qingmo.app.xiaomo.modules

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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

/** choice_panel 互动组件 — 纯代码绘制，无 PNG */
class ChoicePanelModule : InteractionModule {
    override val moduleId = "choice_panel"
    override val moduleName = "选择面板"
    override val priority = 50

    override fun onRegister() {}
    override fun onUnregister() {}

    override fun canHandle(highlight: DramaHighlight): Boolean =
        highlight.interactionType == "choice_panel"

    @Composable
    override fun RenderInteraction(
        highlight: DramaHighlight,
        onInteract: (InteractionResult) -> Unit,
        onDismiss: () -> Unit,
    ) {
        val cfg = highlight.interactionConfig
        val title = (cfg["title"] as? String) ?: "你希望剧情怎么走？"
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

            // 选项列表 — 垂直排列的按钮
            for ((key, label) in options.take(4)) {
                val isSelected = selectedKey == key
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) Color(0xFF4CAF50).copy(alpha = 0.15f)
                                 else Color.White.copy(alpha = 0.08f),
                    animationSpec = tween(200),
                )
                val borderColor by animateColorAsState(
                    targetValue = if (isSelected) Color(0xFF4CAF50).copy(alpha = 0.5f)
                                 else Color.White.copy(alpha = 0.2f),
                    animationSpec = tween(200),
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) Color(0xFF4CAF50)
                                 else Color.White.copy(alpha = 0.8f),
                    animationSpec = tween(200),
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, borderColor, RoundedCornerShape(14.dp))
                        .background(bgColor)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = if (isSelected) "●  $label" else "○  $label",
                        color = textColor,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }

    override fun processResult(result: InteractionResult) {}
}
