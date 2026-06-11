package com.qingmo.app.xiaomo.modules

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingmo.app.data.model.DramaHighlight
import com.qingmo.app.xiaomo.ModuleRegistry

/**
 * 统一互动面板容器 — 黑色半透明浮层，白色细描边，圆角 18dp
 * 内容由 ModuleRegistry 调度的互动模块渲染
 */
@Composable
fun InteractionPanelOverlay(
    highlight: DramaHighlight,
    visible: Boolean,
    onInteract: () -> Unit,
    onDismiss: () -> Unit,
) {
    val panelAlpha by animateFloatAsState(if (visible) 1f else 0f, tween(300))
    var showRing by remember { mutableStateOf(false) }
    var showPlusOne by remember { mutableStateOf(false) }

    val module = remember(highlight.id, highlight.interactionType) {
        ModuleRegistry.findHandler(highlight)
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300)),
        exit = fadeOut(tween(200)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            // 面板背景 — 黑色半透明 + 白色细描边 + 圆角
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(18.dp))
                    .background(Color(0xE6000000))
                    .padding(vertical = 16.dp, horizontal = 4.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // 高光类型标签
                    Text(
                        text = highlight.title.ifEmpty { "高光时刻" },
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Light,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )

                    // 模块内容
                    if (module != null) {
                        module.RenderInteraction(
                            highlight = highlight,
                            onInteract = { result ->
                                showRing = true
                                showPlusOne = true
                                onInteract()
                                module.processResult(result)
                            },
                            onDismiss = onDismiss,
                        )
                    }
                }
            }

            // Canvas 特效层
            ClickRingEffect(triggered = showRing) { showRing = false }
            PlusOneEffect(text = "+1", triggered = showPlusOne) { showPlusOne = false }
        }
    }
}
