package com.qingmo.app.xiaomo.companion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qingmo.app.data.model.DramaHighlight
import com.qingmo.app.xiaomo.XiaoMoCore
import kotlinx.coroutines.delay

/**
 * 小墨助手容器 — 固定右下角 + 面板左上方展开
 *
 * 布局：
 *   [互动面板 Card]  [小墨 Avatar]
 */
@Composable
fun XiaomoCompanion(
    activeHighlight: DramaHighlight?,
    visible: Boolean,
    currentGifCode: String = "idle",
    modifier: Modifier = Modifier,
) {
    val hasActiveHL = activeHighlight != null && visible
    val interactionType = if (hasActiveHL) resolveInteractionType(activeHighlight!!) else ""
    val title = if (hasActiveHL) resolveTitle(activeHighlight) else ""
    val description = if (hasActiveHL) activeHighlight.description ?: "" else ""

    // 本地状态 — 按 highlight id 记忆
    val hlId = activeHighlight?.id ?: 0
    var isClicked by remember(hlId) { mutableStateOf(false) }
    var selectedChip by remember(hlId) { mutableStateOf<String?>(null) }
    var selectedChoice by remember(hlId) { mutableStateOf<String?>(null) }
    var avatarState by remember(hlId) { mutableStateOf(AvatarState.Idle) }
    var panelVisible by remember(hlId) { mutableStateOf(true) }

    // 3-5秒自动收起 + 高光离开收起
    LaunchedEffect(hlId, visible) {
        panelVisible = true
        if (hasActiveHL && !isClicked) {
            delay(5000L)
            panelVisible = false
            avatarState = AvatarState.Idle
        }
        if (!visible || !hasActiveHL) {
            panelVisible = false
            avatarState = AvatarState.Idle
        }
    }

    val screenWidth = LocalConfiguration.current.screenWidthDp.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(end = 12.dp, bottom = 76.dp),
        contentAlignment = Alignment.BottomEnd,
    ) {
        // 互动面板 — 小墨左上方
        AnimatedVisibility(
            visible = panelVisible && hasActiveHL,
            enter = fadeIn(tween(180)) + slideInHorizontally(tween(180)) { it / 4 },
            exit = fadeOut(tween(150)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 64.dp, bottom = 0.dp)
                .widthIn(max = screenWidth - 120.dp),
        ) {
            activeHighlight?.let { hl ->
            when (interactionType) {
                "support_button" -> SupportButtonPanel(
                    title = title,
                    description = description,
                    buttonText = (hl.interactionConfig["button_text"] as? String)
                        ?: defaultButtonText(hl.highlightType),
                    clickedText = (hl.interactionConfig["clicked_text"] as? String)
                        ?: defaultClickedText(hl.highlightType),
                    isClicked = isClicked,
                    onClick = {
                        if (!isClicked) {
                            isClicked = true
                            avatarState = AvatarState.Interacting
                            XiaoMoCore.triggerEffect(hl.xiaomoGifCode)
                        }
                    },
                )

                "reaction_panel" -> ReactionPanel(
                    title = (hl.interactionConfig["title"] as? String) ?: title,
                    options = resolveOptions(hl),
                    selectedKey = selectedChip,
                    onSelect = { key ->
                        if (!isClicked) {
                            isClicked = true
                            selectedChip = key
                            avatarState = AvatarState.Interacting
                            XiaoMoCore.triggerEffect(hl.xiaomoGifCode)
                        }
                    },
                )

                "choice_panel" -> ChoicePanel(
                    title = (hl.interactionConfig["title"] as? String) ?: title,
                    options = resolveOptions(hl),
                    selectedKey = selectedChoice,
                    onSelect = { key ->
                        if (!isClicked) {
                            isClicked = true
                            selectedChoice = key
                            avatarState = AvatarState.Interacting
                            XiaoMoCore.triggerEffect(hl.xiaomoGifCode)
                        }
                    },
                )
            }
            }  // let
        }

        // 小墨 Avatar 已由旧版 XiaoMoPeekView 渲染，此处不再重复
    }
}

/** choice_panel 面板 */
@Composable
fun ChoicePanel(
    title: String,
    options: List<Pair<String, String>>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
) {
    InteractionCard {
        CardTitle(title)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for ((key, label) in options.take(3)) {
                val isSel = selectedKey == key
                val itemBg by animateColorAsState(
                    targetValue = if (isSel) Color(0xFFF5F5F5) else Color.White,
                    animationSpec = tween(200),
                )
                val itemBorder by animateColorAsState(
                    targetValue = if (isSel) Color(0xFF111111) else Color(0xFFD9D9D9),
                    animationSpec = tween(200),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // ○/● 圆点
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .padding(2.dp)
                            .then(
                                if (isSel) Modifier.background(Color(0xFF111111), CircleShape).padding(4.dp)
                                    .background(Color.White, CircleShape)
                                else Modifier.background(Color.White, CircleShape)
                            ),
                    )
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.material3.Text(
                        text = label,
                        color = Color(0xFF111111),
                        fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp),
                        fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

