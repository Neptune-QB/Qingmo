package com.qingmo.app.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.qingmo.app.data.api.RetrofitClient
import com.qingmo.app.data.model.DramaBrief
import com.qingmo.app.data.repository.DramaRepository
import com.qingmo.app.ui.theme.Background
import com.qingmo.app.xiaomo.ui.XiaoMoChatPanel
import com.qingmo.app.data.chat.ChatMessage
import com.qingmo.app.data.chat.ChatService
import com.qingmo.app.data.auth.TokenManager
import com.qingmo.app.data.user.DeviceIdProvider
import com.qingmo.app.ui.theme.GradientCyanDark
import com.qingmo.app.ui.theme.OnSurface
import com.qingmo.app.ui.theme.OnSurfaceVariant
import com.qingmo.app.ui.theme.Primary
import com.qingmo.app.ui.theme.SurfaceElevated
import com.qingmo.app.ui.theme.SurfaceVariant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("ktlint:standard:function-naming")
@Composable
fun DramaListScreen(onDramaClick: (Int) -> Unit, onProfileClick: () -> Unit = {}) {
    val repository = remember { DramaRepository() }
    var dramas by remember { mutableStateOf<List<DramaBrief>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showXiaoMoPage by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showFullChat by remember { mutableStateOf(false) }
    var selectedSessionId by remember { mutableStateOf<Int?>(null) }
    var selectedSessionTitle by remember { mutableStateOf("") }
    var sessions by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var showDeleteDialog by remember { mutableStateOf<Int?>(null) }
    val chatMessages = remember { mutableStateListOf<ChatMessage>() }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentUserId = TokenManager.getUserId().takeIf { it > 0 }?.toString() ?: DeviceIdProvider.getDeviceId(context)

    fun loadDramas() {
        scope.launch {
            isLoading = true
            error = null
            try {
                val result = repository.getDramas()
                result
                    .onSuccess {
                        dramas = it
                        if (it.isEmpty()) {
                            error = "后端返回空列表"
                        }
                    }
                    .onFailure {
                        error = "错误: ${it.message ?: "未知异常"}"
                    }
            } catch (e: Throwable) {
                error = "外层捕获异常: ${e.message ?: e.javaClass.simpleName}"
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadDramas() }

    fun loadSessions() {
        scope.launch(Dispatchers.IO) {
            try {
                sessions = RetrofitClient.api.listSessions(currentUserId)
            } catch (_: Exception) {}
        }
    }

    fun createNewSession() {
        // 先进入对话，等用户发送第一条消息时才创建会话（用首条消息做标题）
        selectedSessionId = -1 // 标记为"待创建"
        selectedSessionTitle = "新对话"
        chatMessages.clear()
    }

    fun openSession(session: Map<String, Any>) {
        selectedSessionId = (session["id"] as? Number)?.toInt() ?: return
        selectedSessionTitle = session["title"] as? String ?: "对话"
        chatMessages.clear()
        scope.launch(Dispatchers.IO) {
            try {
                val msgs = RetrofitClient.api.getSessionMessages(selectedSessionId!!)
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    chatMessages.clear()
                    msgs.forEach { msg ->
                        chatMessages.add(ChatMessage(
                            id = ((msg["id"] as? Number)?.toLong() ?: System.currentTimeMillis()),
                            role = when (msg["role"] as? String) {
                                "user" -> ChatMessage.Role.User
                                else -> ChatMessage.Role.XiaoMo
                            },
                            content = msg["content"] as? String ?: "",
                        ))
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun deleteSession(sessionId: Int) {
        scope.launch(Dispatchers.IO) {
            try {
                RetrofitClient.api.deleteSession(sessionId)
                sessions = RetrofitClient.api.listSessions(currentUserId)
            } catch (_: Exception) {}
        }
    }

    Box(Modifier.fillMaxSize()) {

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
                title = {
                    Text(
                        "青墨",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
                    IconButton(onClick = { showXiaoMoPage = true }) {
                        Icon(
                            Icons.Filled.SmartToy,
                            contentDescription = "小墨",
                            tint = OnSurface,
                        )
                    }
                    IconButton(onClick = onProfileClick) {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = "我的",
                            tint = OnSurface,
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                    ),
            )
        },
        floatingActionButton = {
            Box(Modifier.size(56.dp).background(Color(0xFF7C4DFF), CircleShape).clickable { showSettings = true }, contentAlignment = Alignment.Center) {
                Text("\u2699\uFE0F", fontSize = 24.sp)
            }
        },
        containerColor = Background,
    ) { padding ->
        when {
            isLoading -> LoadingGrid(Modifier.padding(padding))
            error != null ->
                ErrorState(
                    message = error!!,
                    onRetry = { loadDramas() },
                    modifier = Modifier.padding(padding),
                )
            dramas.isEmpty() -> EmptyState(Modifier.padding(padding))
            else ->
                DramaGrid(
                    dramas = dramas,
                    onDramaClick = onDramaClick,
                    modifier = Modifier.padding(padding),
                )
        }
        if (showSettings) com.qingmo.app.ui.components.XiaoMoSettingsSheet { showSettings = false }
    }

    // 小墨功能页面
    if (showXiaoMoPage) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Color(0xFFF7F3EC))
                .windowInsetsPadding(WindowInsets.statusBars)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { }
                .zIndex(99f)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { showXiaoMoPage = false; showFullChat = false }) {
                    Icon(Icons.Filled.Close, "关闭", tint = Color(0xFF333333))
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    "小墨 AI",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333),
                )
            }

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 对话窗口入口
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .clickable { loadSessions(); showFullChat = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(48.dp)
                                .background(Color(0xFF1A535C), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.SmartToy,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "对话窗口",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF333333),
                            )
                            Text(
                                "与小墨 AI 畅聊剧情",
                                fontSize = 13.sp,
                                color = Color(0xFF999999),
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = Color(0xFFBBBBBB),
                        )
                    }
                }
            }
        }
    }

    // 全屏对话
    if (showFullChat) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Color(0xFFF7F3EC))
                .windowInsetsPadding(WindowInsets.statusBars)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { }
                .zIndex(100f)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    if (selectedSessionId != null) {
                        selectedSessionId = null
                        selectedSessionTitle = ""
                        loadSessions()
                    } else {
                        showFullChat = false
                    }
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color(0xFF333333))
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    if (selectedSessionId != null) selectedSessionTitle else "小墨 AI",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333),
                )
            }

            // 会话列表或对话面板
            if (selectedSessionId != null) {
                XiaoMoChatPanel(
                    userId = TokenManager.getUserId().toString(),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    externalMessages = chatMessages,
                    sessionId = selectedSessionId,
                    onCreateSession = { firstMsg ->
                        kotlinx.coroutines.withContext(Dispatchers.IO) {
                            try {
                                val fallbackTitle = if (firstMsg.length > 10) firstMsg.take(10) + "…" else firstMsg
                                val resp = RetrofitClient.api.createSession(mapOf(
                                    "title" to fallbackTitle,
                                    "user_id" to currentUserId,
                                ))
                                val id = (resp["id"] as? Number)?.toInt() ?: -1
                                if (id > 0) {
                                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                                        selectedSessionId = id
                                        selectedSessionTitle = fallbackTitle
                                    }
                                    // 异步用AI生成更好的标题
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            val summary = ChatService.summarizeTitle(firstMsg)
                                            if (summary.isNotEmpty() && summary != fallbackTitle) {
                                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                                    selectedSessionTitle = summary
                                                }
                                                RetrofitClient.api.updateSessionTitle(id, mapOf("title" to summary))
                                            }
                                        } catch (_: Exception) {}
                                    }
                                }
                                id
                            } catch (_: Exception) { -1 }
                        }
                    },
                )
            } else {
                SessionList(
                    sessions = sessions,
                    onNewSession = { createNewSession() },
                    onOpenSession = { openSession(it) },
                    onDeleteSession = { deleteSessionId ->
                        showDeleteDialog = deleteSessionId
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            }
        }

        // 删除确认对话框
        if (showDeleteDialog != null) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showDeleteDialog = null },
                title = { Text("删除对话") },
                text = { Text("确定要删除这个对话吗？") },
                confirmButton = {
                    Text(
                        "删除",
                        color = Color(0xFFE53935),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(8.dp)
                            .clickable {
                                deleteSession(showDeleteDialog!!)
                                showDeleteDialog = null
                            },
                    )
                },
                dismissButton = {
                    Text(
                        "取消",
                        modifier = Modifier
                            .padding(8.dp)
                            .clickable { showDeleteDialog = null },
                    )
                },
            )
        }
    }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionList(
    sessions: List<Map<String, Any>>,
    onNewSession: () -> Unit,
    onOpenSession: (Map<String, Any>) -> Unit,
    onDeleteSession: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 新对话按钮
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNewSession() },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.12f)),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Edit, "新对话", tint = Primary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("新对话", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Primary)
                }
            }
        }

        // 历史对话列表
        items(sessions.reversed()) { session ->
            val id = (session["id"] as? Number)?.toInt() ?: return@items
            val title = session["title"] as? String ?: "对话"
            val updated = session["updated_at"] as? String ?: ""
            val time = if (updated.length >= 16) updated.substring(5, 16) else updated

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onOpenSession(session) },
                        onLongClick = { onDeleteSession(id) },
                    ),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color(0xFF333333))
                        if (time.isNotEmpty()) {
                            Text(time, fontSize = 12.sp, color = Color(0xFF999999), modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = Color(0xFFBBBBBB), modifier = Modifier.size(20.dp))
                }
            }
        }

        // 空状态
        if (sessions.isEmpty()) {
            item {
                Text(
                    "暂无历史对话",
                    fontSize = 14.sp,
                    color = Color(0xFFBBBBBB),
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun LoadingGrid(modifier: Modifier = Modifier) {
    val alpha = 0.4f

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(6) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.75f)
                            .background(
                                Brush.verticalGradient(
                                    colors =
                                        listOf(
                                            SurfaceVariant.copy(alpha = alpha),
                                            Background.copy(alpha = alpha + 0.1f),
                                        ),
                                ),
                            ),
                    )
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Box(
                            Modifier
                                .fillMaxWidth(0.75f)
                                .height(14.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(SurfaceVariant.copy(alpha = alpha)),
                        )
                        Spacer(Modifier.height(8.dp))
                        Box(
                            Modifier
                                .fillMaxWidth(0.45f)
                                .height(10.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(SurfaceVariant.copy(alpha = alpha * 0.7f)),
                        )
                    }
                }
            }
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "加载失败",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onRetry,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = Primary,
                        contentColor = Color.White,
                    ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("重试")
            }
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "暂无短剧内容",
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceVariant,
        )
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun DramaGrid(
    dramas: List<DramaBrief>,
    onDramaClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        itemsIndexed(dramas, key = { _, d -> d.id }) { _, drama ->
            DramaCard(
                drama = drama,
                onClick = { onDramaClick(drama.id) },
            )
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun DramaCard(
    drama: DramaBrief,
    onClick: () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        label = "card_scale",
    )

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .scale(scale)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            // --- 封面图 + 渐变叠加 ---
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.75f),
            ) {
                AsyncImage(
                    model = RetrofitClient.resolveMediaUrl(drama.coverUrl),
                    contentDescription = drama.title,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop,
                )
                // 底部渐变叠加，让文字更清晰
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .align(Alignment.BottomCenter)
                        .background(GradientCyanDark),
                )
                // 集数标签
                if (drama.episodeCount > 0) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.45f),
                    ) {
                        Text(
                            "${drama.episodeCount}集",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
            // --- 标题 + 标签 ---
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(
                    drama.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!drama.tags.isNullOrEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        drama.tags.take(2).forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Primary.copy(alpha = 0.08f),
                            ) {
                                Text(
                                    tag,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Primary,
                                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
