package com.qingmo.app.xiaomo.modules

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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

/** support_button 互动组件 — 纯代码绘制，无 PNG */
class SupportButtonModule : InteractionModule {
    override val moduleId = "support_button"
    override val moduleName = "助力按钮"
    override val priority = 30

    override fun onRegister() {}
    override fun onUnregister() {}

    override fun canHandle(highlight: DramaHighlight): Boolean =
        highlight.interactionType == "support_button"

    @Composable
    override fun RenderInteraction(
        highlight: DramaHighlight,
        onInteract: (InteractionResult) -> Unit,
        onDismiss: () -> Unit,
    ) {
        val cfg = highlight.interactionConfig
        val buttonText = (cfg["button_text"] as? String) ?: "互动一下"
        val clickedText = (cfg["clicked_text"] as? String) ?: "已互动"
        val effectText = (cfg["effect_text"] as? String) ?: "互动值 +1"

        var clicked by remember { mutableStateOf(false) }
        var showEffect by remember { mutableStateOf(false) }

        val panelAlpha by animateFloatAsState(1f, tween(300))

        val buttonBg = if (clicked) Color(0xFF4CAF50).copy(alpha = 0.2f)
                       else Color.White.copy(alpha = 0.12f)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 点击效果文字
            AnimatedVisibility(visible = showEffect, enter = fadeIn(tween(200)), exit = fadeOut(tween(400))) {
                Text(
                    text = effectText,
                    color = Color(0xFF4CAF50),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            // 按钮 — 代码绘制，无 PNG
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .background(buttonBg)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (clicked) {
                    // 已点击态：勾号 + 文字
                    Text(
                        text = "✓  $clickedText",
                        color = Color(0xFF4CAF50),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    // 普通态：居中文字按钮
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF333333))
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = buttonText,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }

    override fun processResult(result: InteractionResult) {}
}
