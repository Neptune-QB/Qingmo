package com.qingmo.app.xiaomo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.qingmo.app.xiaomo.ui.GifImage
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
    sessionId: Int? = null,
    onCreateSession: (suspend (title: String) -> Int)? = null,
    onLinkClick: ((Int) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val messages = externalMessages ?: remember { mutableStateListOf<ChatMessage>() }
    var inputText by remember { mutableStateOf("") }
    var isStreaming by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // 外部传入persistentMessages已初始化欢迎消息，退出抽屉完全保留历史记录

    // 自动滚动到最新消息
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = modifier.fillMaxWidth().imePadding()) {
        // 消息列表
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(messages.toList(), key = { it.id }) { msg ->
                ChatBubble(message = msg, showTypingIndicator = isStreaming && msg.isStreaming && msg.content.isEmpty(), onLinkClick = onLinkClick)
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
                    // 新会话：先创建会话拿到 ID
                    var effectiveSessionId = sessionId
                    if (effectiveSessionId != null && effectiveSessionId <= 0 && onCreateSession != null) {
                        val title = if (text.length > 10) text.take(10) + "…" else text
                        effectiveSessionId = onCreateSession(title)
                    }

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
                        // 持久化：保存用户消息和小墨回复到后端
                        if (effectiveSessionId != null && effectiveSessionId > 0) {
                            try {
                                com.qingmo.app.data.api.RetrofitClient.api.appendMessage(mapOf(
                                    "session_id" to effectiveSessionId,
                                    "role" to "user",
                                    "content" to text,
                                ))
                                com.qingmo.app.data.api.RetrofitClient.api.appendMessage(mapOf(
                                    "session_id" to effectiveSessionId,
                                    "role" to "assistant",
                                    "content" to messages[msgIndex].content,
                                ))
                            } catch (_: Exception) {}
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
 * 合并文字内容与打字指示器为同一个气泡
 */
@Composable
private fun ChatBubble(message: ChatMessage, showTypingIndicator: Boolean = false, onLinkClick: ((Int) -> Unit)? = null) {
    val isUser = message.role == ChatMessage.Role.User
    val contentEmpty = message.content.isEmpty() && !showTypingIndicator

    val bgColor = if (isUser) Primary.copy(alpha = 0.15f) else SurfaceVariant.copy(alpha = 0.4f)
    val textColor = if (isUser) Primary else OnSurface
    val shape = RoundedCornerShape(
        topStart = 12.dp,
        topEnd = 12.dp,
        bottomStart = if (isUser) 12.dp else 4.dp,
        bottomEnd = if (isUser) 4.dp else 12.dp,
    )

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        // 小墨头像（左侧）—— 始终渲染
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .padding(top = 8.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                GifImage(
                    resId = com.qingmo.app.R.raw.xiaomo,
                    contentDescription = "小墨",
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.width(8.dp))
        }

        if (!contentEmpty) {
            Box(
                modifier = Modifier
                    .widthIn(max = 220.dp)
                    .clip(shape)
                    .background(bgColor)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Column {
                    if (message.content.isNotEmpty()) {
                        // 匹配两种链接格式：
                        // 1. 👉「点我立刻看《剧名》」<qingmo://play?drama_id=1>
                        // 2. 纯链接 <qingmo://play?drama_id=1>
                        val linkPattern = Regex("""(?:👉\s*「([^」]+)」)?<qingmo://play\?drama_id=(\d+)>""")
                        val parts = message.content.split(linkPattern)
                        if (parts.size > 1) {
                            var i = 0
                            while (i < parts.size) {
                                if (parts[i].isNotEmpty()) {
                                    Text(
                                        text = parts[i],
                                        color = textColor,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp,
                                    )
                                }
                                i++
                                if (i + 1 < parts.size) {
                                    val title = parts[i]?.takeIf { it.isNotEmpty() } ?: "这部短剧"
                                    val dramaId = parts[i + 1]  // drama_id
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 4.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF1E88E5).copy(alpha = 0.1f))
                                            .clickable { onLinkClick?.invoke(dramaId.toIntOrNull() ?: 0) }
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                    ) {
                                        Text(
                                            text = if (title == "这部短剧") "👉「点我立刻看」" else "👉「点我立刻看《$title》」",
                                            color = Color(0xFF1E88E5),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    }
                                    i += 2
                                }
                            }
                        } else {
                            Text(
                                text = message.content,
                                color = textColor,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                            )
                        }
                    }
                    // 流式输出打字指示器直接追加在同一个气泡底部
                    if (showTypingIndicator) {
                        if (message.content.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                        }
                        Text(
                            text = "小墨正在输入...",
                            color = OnSurfaceMuted,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }

        // 用户头像（右侧）
        if (isUser) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .padding(top = 8.dp)
                    .clip(CircleShape)
                    .background(Primary.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("👤", fontSize = 16.sp)
            }
        }
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
            modifier = Modifier.weight(1f),
            textStyle = TextStyle(
                color = OnSurface,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            ),
            cursorBrush = SolidColor(Primary),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = "和 小墨 说点什么...",
                            color = OnSurfaceMuted,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                        )
                    }
                    innerTextField()
                }
            },
            enabled = enabled,
            singleLine = true,
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
