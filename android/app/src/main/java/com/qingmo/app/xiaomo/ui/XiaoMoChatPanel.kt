package com.qingmo.app.xiaomo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingmo.app.data.chat.ChatMessage
import com.qingmo.app.data.chat.ChatService
import com.qingmo.app.ui.theme.Border
import com.qingmo.app.ui.theme.OnPrimary
import com.qingmo.app.ui.theme.OnSurface
import com.qingmo.app.ui.theme.OnSurfaceMuted
import com.qingmo.app.ui.theme.Primary
import com.qingmo.app.ui.theme.SurfaceVariant
import com.qingmo.app.xiaomo.XiaoMoEmotion
import com.qingmo.app.xiaomo.XiaoMoCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 小墨对话面板 — 完整的 AI 聊天界面
 *
 * 包含：消息列表、流式输入展示、发送按钮
 * 默认展示欢迎消息，支持多轮对话
 */
@Composable
fun XiaoMoChatPanel(
    modifier: Modifier = Modifier,
    userId: String = "android-demo",
    dramaContext: Map<String, Any>? = null,
    externalMessages: androidx.compose.runtime.snapshots.SnapshotStateList<ChatMessage>? = null,
) {
    val scope = rememberCoroutineScope()
    val messages = externalMessages ?: remember { mutableStateListOf<ChatMessage>() }
    var inputText by remember { mutableStateOf("") }
    var isStreaming by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // 首次进入显示欢迎消息
    LaunchedEffect(Unit) {
        if (messages.isEmpty()) {
            messages.add(
                ChatMessage(
                    id = 0,
                    role = ChatMessage.Role.XiaoMo,
                    content = "嗨！我是小墨，你的 AI 观剧伙伴~\n有什么想聊的吗？可以问我推荐短剧、讨论剧情，或者问我的看法哦！✨",
                ),
            )
        }
    }

    // 自动滚动到最新消息
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // 消息列表
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(messages.toList(), key = { it.id }) { msg ->
                ChatBubble(message = msg)
            }

            // 流式输出中显示加载指示器
            if (isStreaming) {
                item(key = "typing") {
                    TypingIndicator()
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // 输入区域
        ChatInputField(
            value = inputText,
            onValueChange = { inputText = it },
            onSend = {
                val text = inputText.trim()
                if (text.isEmpty() || isStreaming) return@ChatInputField

                val userMsg = ChatMessage(
                    id = System.currentTimeMillis(),
                    role = ChatMessage.Role.User,
                    content = text,
                )
                messages.add(userMsg)
                inputText = ""
                isStreaming = true

                // 构建历史消息
                val history = messages.dropLast(1).map {
                    mapOf(
                        "role" to if (it.role == ChatMessage.Role.User) "user" else "assistant",
                        "content" to it.content,
                    )
                }

                scope.launch {
                    val xiaoMoMsg = ChatMessage(
                        id = System.currentTimeMillis() + 1,
                        role = ChatMessage.Role.XiaoMo,
                        content = "",
                        isStreaming = true,
                    )
                    messages.add(xiaoMoMsg)
                    val msgIndex = messages.size - 1

                    try {
                        ChatService.streamChat(
                            userMessage = text,
                            userId = userId,
                            history = history,
                            dramaContext = dramaContext,
                        ).collect { chunk ->
                            withContext(Dispatchers.Main) {
                                val current = messages[msgIndex]
                                messages[msgIndex] = current.copy(
                                    content = current.content + chunk,
                                )
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            messages[msgIndex] = messages[msgIndex].copy(
                                content = messages[msgIndex].content + "\n(对话中断：${e.message})",
                            )
                        }
                    } finally {
                        withContext(Dispatchers.Main) {
                            messages[msgIndex] = messages[msgIndex].copy(isStreaming = false)
                            isStreaming = false
                        }
                    }
                }
            },
            enabled = !isStreaming,
        )
    }
}

/**
 * 单条聊天气泡
 */
@Composable
private fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == ChatMessage.Role.User
    val bgColor = if (isUser) Primary.copy(alpha = 0.15f) else SurfaceVariant.copy(alpha = 0.4f)
    val textColor = if (isUser) Primary else OnSurface
    val shape = RoundedCornerShape(
        topStart = 12.dp,
        topEnd = 12.dp,
        bottomStart = if (isUser) 12.dp else 4.dp,
        bottomEnd = if (isUser) 4.dp else 12.dp,
    )
    val alignment = if (isUser) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalAlignment = alignment,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 220.dp)
                .clip(shape)
                .background(bgColor)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(
                text = message.content,
                color = textColor,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
    }
}

/**
 * 打字指示器 — 三个点动画
 */
@Composable
private fun TypingIndicator() {
    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = "小墨正在输入...",
            color = OnSurfaceMuted,
            fontSize = 12.sp,
        )
    }
}

/**
 * 底部输入栏
 */
@Composable
private fun ChatInputField(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Border.copy(alpha = 0.2f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).padding(vertical = 4.dp),
            textStyle = TextStyle(
                color = OnSurface,
                fontSize = 13.sp,
            ),
            cursorBrush = SolidColor(Primary),
            decorationBox = { innerTextField ->
                if (value.isEmpty()) {
                    Text(
                        text = "和 小墨 说点什么...",
                        color = OnSurfaceMuted,
                        fontSize = 13.sp,
                    )
                }
                innerTextField()
            },
            enabled = enabled,
        )

        Spacer(Modifier.width(4.dp))

        IconButton(
            onClick = onSend,
            enabled = enabled && value.isNotBlank(),
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "发送",
                tint = if (value.isNotBlank()) Primary else Border,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
