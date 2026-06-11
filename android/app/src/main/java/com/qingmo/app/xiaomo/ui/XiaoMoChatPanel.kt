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
import androidx.compose.foundation.lazy.LazyRow
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
import android.graphics.BitmapFactory
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.qingmo.app.xiaomo.ui.GifImage
import com.qingmo.app.data.auth.TokenManager
import com.qingmo.app.data.chat.ChatMessage
import com.qingmo.app.data.chat.ChatService
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.qingmo.app.ui.theme.Border
import com.qingmo.app.ui.theme.GraphiteTeal
import com.qingmo.app.ui.theme.GraphiteTealSoft
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
import kotlin.random.Random

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
    var lastRecommendedDramaId by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    // 外部传入persistentMessages已初始化欢迎消息，退出抽屉完全保留历史记录

    // 自动滚动到最新消息
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    fun sendMessage(text: String) {
        val cleanText = text.trim()
        if (cleanText.isEmpty() || isStreaming) return

        val userMsg = ChatMessage(
            id = System.currentTimeMillis(),
            role = ChatMessage.Role.User,
            content = cleanText,
        )
        messages.add(userMsg)
        inputText = ""
        isStreaming = true

        val history = messages.dropLast(1).map {
            mapOf(
                "role" to if (it.role == ChatMessage.Role.User) "user" else "assistant",
                "content" to it.content,
            )
        }

        scope.launch {
            var effectiveSessionId = sessionId
            if (effectiveSessionId != null && effectiveSessionId <= 0 && onCreateSession != null) {
                val title = if (cleanText.length > 10) cleanText.take(10) + "…" else cleanText
                effectiveSessionId = onCreateSession(title)
            }

            val localReply = buildLocalRecommendationReply(cleanText, lastRecommendedDramaId)
            if (localReply != null) {
                lastRecommendedDramaId = localReply.dramaId
                messages.add(
                    ChatMessage(
                        id = System.currentTimeMillis() + 1,
                        role = ChatMessage.Role.XiaoMo,
                        content = localReply.content,
                    )
                )
                isStreaming = false
                persistChatMessage(effectiveSessionId, cleanText, localReply.content)
                return@launch
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
                    userMessage = cleanText,
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
                persistChatMessage(effectiveSessionId, cleanText, messages[msgIndex].content)
            }
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
                ChatBubble(
                    message = msg,
                    showTypingIndicator = isStreaming && msg.isStreaming && msg.content.isEmpty(),
                    onLinkClick = onLinkClick,
                    onQuickPrompt = { prompt -> sendMessage(prompt) },
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        QuickPromptChips(
            enabled = !isStreaming,
            onPromptClick = { prompt -> sendMessage(prompt) },
        )

        Spacer(Modifier.height(6.dp))

        // 输入区域
        ChatInputField(
            value = inputText,
            onValueChange = { inputText = it },
            onSend = { sendMessage(inputText) },
            enabled = !isStreaming,
        )
    }
}

private data class DramaRecommendMeta(
    val id: String,
    val title: String,
    val tags: List<String>,
    val moodTags: List<String>,
    val sceneTags: List<String>,
    val recommendReason: String,
    val highlightText: String,
)

private data class LocalRecommendationReply(
    val dramaId: String,
    val content: String,
)

private val quickPrompts = listOf(
    "帮我挑一部",
    "换一部",
    "想看爽一点",
    "想看甜一点",
    "随便看看",
)

private val dramaRecommendMetas = listOf(
    DramaRecommendMeta(
        id = "1",
        title = "北派寻宝笔记",
        tags = listOf("志怪", "盗墓", "寻宝探险"),
        moodTags = listOf("爽感", "反转", "大男主"),
        sceneTags = listOf("通勤", "睡前", "想看冒险"),
        recommendReason = "它的推进节奏直接，寻宝、江湖和反转都比较集中，适合不知道看什么时先用一部冒险感强的剧打开状态。",
        highlightText = "穷小子踏入北方江湖，一路闯险地、识人心，短时间内就能看到悬念和成长线。",
    ),
    DramaRecommendMeta(
        id = "2",
        title = "天下第一纨绔",
        tags = listOf("古装", "马甲", "比武招亲"),
        moodTags = listOf("爽感", "反转", "逆袭"),
        sceneTags = listOf("下饭", "想看打脸", "轻松看"),
        recommendReason = "主角表面纨绔、实际藏实力，打脸点来得很快，适合想看一部不费脑但有爆点的古装爽剧。",
        highlightText = "比武场上一拳破局，身份反转和压场面桥段很适合连续刷几集。",
    ),
    DramaRecommendMeta(
        id = "3",
        title = "十八岁太奶奶驾到，重整家族荣耀第三部",
        tags = listOf("穿越", "大女主", "亲情"),
        moodTags = listOf("爽感", "逆袭", "打脸虐渣"),
        sceneTags = listOf("想看女强", "家庭线", "周末看"),
        recommendReason = "它把穿越、家族整顿和大女主控场放在一起，冲突明确，适合想看女主掌控局面的爽感故事。",
        highlightText = "十八岁外表加太奶奶辈分的设定很抓人，整顿家族的桥段有记忆点。",
    ),
    DramaRecommendMeta(
        id = "4",
        title = "幸得相遇离婚时",
        tags = listOf("都市爱情", "先婚后爱", "总裁"),
        moodTags = listOf("甜宠", "都市爱情", "先婚后爱", "逆袭"),
        sceneTags = listOf("睡前", "想看恋爱", "情绪修复"),
        recommendReason = "它从离婚后的低谷切入，后面是先婚后爱和共同反击，甜感和逆袭感都有，适合想看情绪被托住的一部。",
        highlightText = "女主重新成长，和男主从合作到心动，爱情线与报复线同步推进。",
    ),
    DramaRecommendMeta(
        id = "5",
        title = "荒年全村啃树皮，我有系统满仓肉",
        tags = listOf("穿越", "系统", "种田经营"),
        moodTags = listOf("爽感", "逆袭", "女强"),
        sceneTags = listOf("下饭", "慢慢看", "家庭线"),
        recommendReason = "它的看点是用系统物资解决现实困境，目标清楚，适合想看经营、囤货和一家人变好的故事。",
        highlightText = "荒年求生加系统开仓，女主带全家过难关的过程很有获得感。",
    ),
    DramaRecommendMeta(
        id = "6",
        title = "家里家外",
        tags = listOf("年代爱情", "家庭伦理", "闪婚"),
        moodTags = listOf("温情", "甜宠", "亲情"),
        sceneTags = listOf("陪家人看", "睡前", "想看生活感"),
        recommendReason = "这部更偏生活流和家庭温情，冲突不重，适合想放松一下、看人物慢慢靠近的时候。",
        highlightText = "重组家庭从磨合到互相支持，年代感和烟火气比较突出。",
    ),
    DramaRecommendMeta(
        id = "7",
        title = "云渺1：我修仙多年强亿点怎么了",
        tags = listOf("都市玄幻", "大女主", "打脸虐渣"),
        moodTags = listOf("爽感", "反转", "逆袭"),
        sceneTags = listOf("想看开挂", "下饭", "碎片时间"),
        recommendReason = "女主能力强、出场就能改变局面，适合想看强者入局、快速打破误会和压制感的剧。",
        highlightText = "修仙大佬进入现代权贵家庭局，身份和实力反差是主要爽点。",
    ),
    DramaRecommendMeta(
        id = "8",
        title = "撕夜",
        tags = listOf("重生", "病娇", "现代言情"),
        moodTags = listOf("反转", "逆袭", "都市爱情"),
        sceneTags = listOf("想看复仇", "夜里看", "情绪浓一点"),
        recommendReason = "它的复仇动机清楚，重生后女主主动改写关系，适合想看更浓烈、更有拉扯感的现代言情。",
        highlightText = "前世真相、重生反击和病娇大佬的深情线交织，情绪张力比较足。",
    ),
    DramaRecommendMeta(
        id = "9",
        title = "那年冬至",
        tags = listOf("都市爱情", "先婚后爱", "闪婚"),
        moodTags = listOf("甜宠", "都市爱情", "先婚后爱"),
        sceneTags = listOf("睡前", "想看甜一点", "放松看"),
        recommendReason = "它是更轻柔的甜向选择，闪婚开局但重心在治愈和双向奔赴，适合想看甜一点的时候。",
        highlightText = "女主像一束光照进男主低谷，婚后日常从试探走向真心。",
    ),
    DramaRecommendMeta(
        id = "10",
        title = "北往",
        tags = listOf("剧情", "喜剧", "小人物"),
        moodTags = listOf("温情", "轻松", "爽感"),
        sceneTags = listOf("随便看看", "下饭", "想看东北味"),
        recommendReason = "它不靠复杂设定，靠一路上的人情、笑点和仗义感推进，适合想随便看看但又希望有温度的时候。",
        highlightText = "两个东北汉子骑摩托返乡，路上救人、拆骗局，轻松里带着热乎劲。",
    ),
)

private fun buildLocalRecommendationReply(
    prompt: String,
    lastRecommendedDramaId: String?,
): LocalRecommendationReply? {
    val candidates = when (prompt) {
        "帮我挑一部" -> dramaRecommendMetas.sortedBy { it.id.toIntOrNull() ?: Int.MAX_VALUE }
        "换一部" -> dramaRecommendMetas.filter { it.id != lastRecommendedDramaId }
        "想看爽一点" -> dramaRecommendMetas.filter {
            it.moodTags.any { tag -> tag in listOf("爽感", "反转", "逆袭") }
        }
        "想看甜一点" -> dramaRecommendMetas.filter {
            it.moodTags.any { tag -> tag in listOf("甜宠", "都市爱情", "先婚后爱") }
        }
        "随便看看" -> dramaRecommendMetas.shuffled(Random(System.currentTimeMillis()))
        else -> return null
    }.ifEmpty { dramaRecommendMetas }

    val meta = when (prompt) {
        "帮我挑一部" -> candidates.first()
        "换一部" -> candidates.random()
        "随便看看" -> candidates.first()
        else -> candidates.first()
    }

    return LocalRecommendationReply(
        dramaId = meta.id,
        content = """
            《${meta.title}》

            ${meta.recommendReason}

            看点：${meta.highlightText}
            <qingmo://play?drama_id=${meta.id}>
        """.trimIndent(),
    )
}

private suspend fun persistChatMessage(
    sessionId: Int?,
    userText: String,
    assistantText: String,
) {
    if (sessionId == null || sessionId <= 0) return
    try {
        com.qingmo.app.data.api.RetrofitClient.api.appendMessage(
            mapOf(
                "session_id" to sessionId,
                "role" to "user",
                "content" to userText,
            )
        )
        com.qingmo.app.data.api.RetrofitClient.api.appendMessage(
            mapOf(
                "session_id" to sessionId,
                "role" to "assistant",
                "content" to assistantText,
            )
        )
    } catch (_: Exception) {
    }
}

@Composable
private fun QuickPromptChips(
    enabled: Boolean,
    onPromptClick: (String) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
    ) {
        items(quickPrompts) { prompt ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (enabled) GraphiteTealSoft else Color(0xFFFAFAFA))
                    .clickable(enabled = enabled) { onPromptClick(prompt) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Text(
                    text = prompt,
                    color = if (enabled) GraphiteTeal else OnSurfaceMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/**
 * 单条聊天气泡
 * 合并文字内容与打字指示器为同一个气泡
 */
@Composable
private fun ChatBubble(
    message: ChatMessage,
    showTypingIndicator: Boolean = false,
    onLinkClick: ((Int) -> Unit)? = null,
    onQuickPrompt: ((String) -> Unit)? = null,
) {
    val isUser = message.role == ChatMessage.Role.User
    val contentEmpty = message.content.isEmpty() && !showTypingIndicator

    val bgColor = if (isUser) Color(0xFFF1F1F1) else Color.White
    val textColor = Color(0xFF222222)
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
            coil.compose.AsyncImage(
                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data("file:///android_asset/xiaomo_avatar.png")
                    .crossfade(true)
                    .build(),
                contentDescription = "小墨",
                modifier = Modifier
                    .size(36.dp)
                    .padding(top = 8.dp),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            )
            Spacer(Modifier.width(8.dp))
        }

        if (!contentEmpty) {
            Box(
                modifier = Modifier
                    .widthIn(max = 220.dp)
                    .clip(shape)
                    .background(bgColor)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Column {
                    if (message.content.isNotEmpty()) {
                        val playPattern = Regex("""<qingmo://play\?drama_id=(\d+)>""")
                        val playId = playPattern.find(message.content)?.groupValues?.getOrNull(1)?.toIntOrNull()
                        val displayContent = message.content.replace(playPattern, "").trim()
                        Text(
                            text = displayContent,
                            color = textColor,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        )
                        if (!isUser && playId != null) {
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                RecommendationActionButton(
                                    text = "开始观看",
                                    onClick = { onLinkClick?.invoke(playId) },
                                )
                                RecommendationActionButton(
                                    text = "换一部",
                                    onClick = { onQuickPrompt?.invoke("换一部") },
                                )
                            }
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
            val ctx = LocalContext.current
            val scope = rememberCoroutineScope()
            var userAvatarBmp by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
            LaunchedEffect(Unit) {
                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    val avatarStr = TokenManager.getAvatar() ?: ""
                    if (avatarStr.isNotEmpty()) {
                        try {
                            val base64Part = avatarStr.removePrefix("data:image/jpeg;base64,")
                            val bytes = java.util.Base64.getDecoder().decode(base64Part)
                            userAvatarBmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        } catch (_: Exception) {}
                    }
                }
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .padding(top = 8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF1F1F1)),
                contentAlignment = Alignment.Center,
            ) {
                userAvatarBmp?.let { bmp ->
                    AsyncImage(
                        model = ImageRequest.Builder(ctx).data(bmp).crossfade(true).build(),
                        contentDescription = "用户头像",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } ?: run {
                    Text("👤", fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun RecommendationActionButton(
    text: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(GraphiteTeal)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
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
            .background(Color(0xFFF4F4F4))
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
            cursorBrush = SolidColor(GraphiteTeal),
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
                tint = if (value.isNotBlank()) GraphiteTeal else Border,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
