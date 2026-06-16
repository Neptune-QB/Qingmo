package com.qingmo.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.activity.compose.BackHandler
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import coil.compose.AsyncImage
import com.qingmo.app.data.api.RetrofitClient
import com.qingmo.app.data.model.DramaBrief
import com.qingmo.app.data.repository.DramaRepository
import com.qingmo.app.ui.theme.Background
import com.qingmo.app.ui.theme.CardIconPurple
import com.qingmo.app.ui.theme.CardIconTeal
import com.qingmo.app.ui.theme.DividerLight
import com.qingmo.app.ui.theme.DividerLighter
import com.qingmo.app.xiaomo.ui.XiaoMoChatPanel
import com.qingmo.app.data.chat.ChatMessage
import com.qingmo.app.data.chat.ChatService
import com.qingmo.app.data.auth.TokenManager
import com.qingmo.app.data.user.DeviceIdProvider
import com.qingmo.app.ui.theme.GradientCyanDark
import com.qingmo.app.ui.theme.GraphiteTeal
import com.qingmo.app.ui.theme.OnSurface
import com.qingmo.app.ui.theme.OnSurfaceVariant
import com.qingmo.app.ui.theme.Primary
import com.qingmo.app.ui.theme.SurfaceElevated
import com.qingmo.app.ui.theme.SurfaceVariant
import com.qingmo.app.ui.theme.TextPrimary
import com.qingmo.app.ui.theme.TextSecondary
import com.qingmo.app.ui.theme.TextDisabled
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("ktlint:standard:function-naming")
@Composable
fun DramaListScreen(
    onDramaClick: (Int) -> Unit,
    onProfileClick: () -> Unit = {}
) {
    val repository = remember { DramaRepository() }
    var dramas by remember { mutableStateOf<List<DramaBrief>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing)
    var error by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    val filteredDramas = remember(dramas, searchQuery) {
        if (searchQuery.isBlank()) dramas
        else dramas.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }
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

    suspend fun doLoad() {
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

    fun loadDramas() {
        scope.launch { doLoad() }
    }

    fun refreshDramas() {
        scope.launch {
            isRefreshing = true
            doLoad()
            delay(500L)
            isRefreshing = false
        }
    }

    fun loadSessions() {
        scope.launch(Dispatchers.IO) {
            try {
                sessions = RetrofitClient.api.listSessions(currentUserId)
            } catch (_: Exception) {}
        }
    }

    fun createNewSession() {
        selectedSessionId = -1
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
                withContext(Dispatchers.Main) {
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

    LaunchedEffect(Unit) { loadDramas() }

    Box(Modifier.fillMaxSize()) {
        SwipeRefresh(
            state = swipeRefreshState,
            onRefresh = { refreshDramas() },
            modifier = Modifier.fillMaxSize(),
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
                        title = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "青墨",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = GraphiteTeal,
                                )
                                Spacer(Modifier.width(10.dp))
                                var tfFocused by remember { mutableStateOf(false) }
                                val tfBorderColor by animateColorAsState(
                                    targetValue = if (tfFocused) GraphiteTeal
                                                   else OnSurfaceVariant.copy(alpha = 0.25f),
                                    label = "tfBorder",
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp),
                                ) {
                                    Surface(
                                        modifier = Modifier.fillMaxSize(),
                                        shape = RoundedCornerShape(22.dp),
                                        color = Color.White,
                                        border = BorderStroke(1.dp, tfBorderColor),
                                    ) {}
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 12.dp)
                                            .onFocusChanged { tfFocused = it.isFocused },
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            Icons.Filled.Search,
                                            contentDescription = "搜索",
                                            tint = OnSurfaceVariant,
                                            modifier = Modifier.size(20.dp),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier.weight(1f),
                                            contentAlignment = Alignment.CenterStart,
                                        ) {
                                            if (searchQuery.isEmpty()) {
                                                Text(
                                                    "搜索短剧...",
                                                    color = OnSurfaceVariant.copy(alpha = 0.6f),
                                                    fontSize = 14.sp,
                                                )
                                            }
                                            BasicTextField(
                                                value = searchQuery,
                                                onValueChange = { searchQuery = it },
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true,
                                                cursorBrush = SolidColor(GraphiteTeal),
                                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                                    color = OnSurface,
                                                ),
                                            )
                                        }
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(
                                                onClick = { searchQuery = "" },
                                                modifier = Modifier.size(24.dp),
                                            ) {
                                                Icon(
                                                    Icons.Filled.Close,
                                                    contentDescription = "清除",
                                                    tint = OnSurfaceVariant,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.width(4.dp))
                                IconButton(onClick = { showXiaoMoPage = true }) {
                                    Icon(
                                        Icons.Filled.SmartToy,
                                        contentDescription = "小墨",
                                        tint = GraphiteTeal,
                                    )
                                }
                                IconButton(onClick = onProfileClick) {
                                    Icon(
                                        Icons.Filled.Person,
                                        contentDescription = "我的",
                                        tint = OnSurface,
                                    )
                                }
                            }
                        },
                        actions = {},
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Background,
                            titleContentColor = MaterialTheme.colorScheme.onBackground,
                        ),
                    )
                },
                containerColor = Background,
            ) { padding ->
                when {
                    isLoading && dramas.isEmpty() -> LoadingGrid(Modifier.padding(padding))
                    error != null ->
                        ErrorState(
                            message = error!!,
                            onRetry = { loadDramas() },
                            modifier = Modifier.padding(padding),
                        )
                    filteredDramas.isEmpty() -> EmptyState(Modifier.padding(padding))
                    else ->
                        DramaGrid(
                            dramas = filteredDramas,
                            onDramaClick = { id ->
                                scope.launch {
                                    delay(150L)
                                    onDramaClick(id)
                                }
                            },
                            modifier = Modifier.padding(padding),
                        )
                }
            }
        }

        if (showSettings) {
            Box(Modifier.fillMaxSize().background(Background).windowInsetsPadding(WindowInsets.statusBars).zIndex(99f)) {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showSettings = false }) { Icon(Icons.Filled.Close, "返回", tint = TextPrimary) }
                        Spacer(Modifier.width(4.dp))
                        Text("⚙ 功能设置", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    Box(Modifier.fillMaxWidth().height(0.5.dp).background(DividerLight))
                    LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                        items(com.qingmo.app.xiaomo.XiaoMoSettings.FEATURES.size) { idx ->
                            val feat = com.qingmo.app.xiaomo.XiaoMoSettings.FEATURES[idx]
                            val key = feat.first
                            val label = feat.second
                            var enabled by remember { mutableStateOf(com.qingmo.app.xiaomo.XiaoMoSettings.isEnabled(key)) }
                            Row(Modifier.fillMaxWidth().height(52.dp).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(label, fontSize = 15.sp, color = TextPrimary)
                                Switch(
                                    checked = enabled,
                                    onCheckedChange = { v ->
                                        enabled = v
                                        com.qingmo.app.xiaomo.XiaoMoSettings.setEnabled(key, v)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Primary,
                                        checkedTrackColor = Primary.copy(alpha = 0.3f),
                                    )
                                )
                            }
                            if (idx < com.qingmo.app.xiaomo.XiaoMoSettings.FEATURES.size - 1) {
                                Box(Modifier.fillMaxWidth().height(0.5.dp).background(DividerLighter))
                            }
                        }
                        item {
                            Spacer(Modifier.height(20.dp))
                            Text(
                                "恢复默认设置",
                                fontSize = 14.sp,
                                color = TextSecondary,
                                modifier = Modifier.clickable {
                                    com.qingmo.app.xiaomo.XiaoMoSettings.resetAll()
                                    showSettings = false
                                    showSettings = true
                                }.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }

        if (showXiaoMoPage && !showSettings && !showFullChat) {
            BackHandler { showXiaoMoPage = false }
            Column(
                Modifier
                    .fillMaxSize()
                    .background(Background)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
                    .zIndex(99f)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { showXiaoMoPage = false; showFullChat = false }) {
                        Icon(Icons.Filled.Close, "关闭", tint = TextPrimary)
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "小墨 AI",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                }

                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable {
                            loadSessions()
                            showFullChat = true
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(com.qingmo.app.R.drawable.xiaomo_chat),
                                contentDescription = "和小墨聊天",
                                modifier = Modifier.size(44.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("和小墨聊天", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                Text("让小墨帮你快速做选择", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(top = 2.dp))
                            }
                            Icon(Icons.Filled.ChevronRight, null, tint = TextDisabled)
                        }
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable {
                            showSettings = true
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(44.dp).background(CardIconPurple, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Edit, null, tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("小墨能力", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                Text("管理小墨能力", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(top = 2.dp))
                            }
                            Icon(Icons.Filled.ChevronRight, null, tint = TextDisabled)
                        }
                    }
                }
            }
        }

        if (showFullChat) {
            BackHandler {
                if (selectedSessionId != null) {
                    selectedSessionId = null
                    selectedSessionTitle = ""
                    loadSessions()
                } else {
                    showFullChat = false
                }
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .background(Background)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary)
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "小墨 AI",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                }

                if (selectedSessionId != null) {
                    XiaoMoChatPanel(
                        userId = TokenManager.getUserId().toString(),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        externalMessages = chatMessages,
                        sessionId = selectedSessionId,
                        onLinkClick = { dramaId ->
                            showFullChat = false
                            showXiaoMoPage = false
                            onDramaClick(dramaId)
                        },
                        onCreateSession = { firstMsg ->
                            withContext(Dispatchers.IO) {
                                try {
                                    val fallbackTitle = if (firstMsg.length > 10) firstMsg.take(10) + "…" else firstMsg
                                    val resp = RetrofitClient.api.createSession(mapOf(
                                        "title" to fallbackTitle,
                                        "user_id" to currentUserId,
                                    ))
                                    val id = (resp["id"] as? Number)?.toInt() ?: -1
                                    if (id > 0) {
                                        withContext(Dispatchers.Main) {
                                            selectedSessionId = id
                                            selectedSessionTitle = fallbackTitle
                                        }
                                        scope.launch(Dispatchers.IO) {
                                            try {
                                                val summary = ChatService.summarizeTitle(firstMsg)
                                                if (summary.isNotEmpty() && summary != fallbackTitle) {
                                                    withContext(Dispatchers.Main) {
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
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }
            }

            if (showDeleteDialog != null) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showDeleteDialog = null },
                    title = { Text("删除对话") },
                    text = { Text("确定要删除这个对话吗？") },
                    confirmButton = {
                        Text(
                            "删除",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(8.dp).clickable {
                                deleteSession(showDeleteDialog!!)
                                showDeleteDialog = null
                            }
                        )
                    },
                    dismissButton = {
                        Text(
                            "取消",
                            modifier = Modifier.padding(8.dp).clickable { showDeleteDialog = null }
                        )
                    }
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
                colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                        if (time.isNotEmpty()) {
                            Text(time, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = TextDisabled, modifier = Modifier.size(20.dp))
                }
            }
        }

        if (sessions.isEmpty()) {
            item {
                Text(
                    "暂无历史对话",
                    fontSize = 14.sp,
                    color = TextDisabled,
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun LoadingGrid(modifier: Modifier = Modifier) {
    val shimmerTransition = rememberInfiniteTransition(label = "shimmer")
    val translateX by shimmerTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_x"
    )

    val shimmerBrush = Brush.horizontalGradient(
        colors = listOf(
            SurfaceVariant.copy(alpha = 0.3f),
            SurfaceVariant.copy(alpha = 0.8f),
            SurfaceVariant.copy(alpha = 0.3f),
        ),
        startX = -300f + translateX,
        endX = 300f + translateX,
    )

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
                            .background(shimmerBrush),
                    )
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Box(
                            Modifier
                                .fillMaxWidth(0.75f)
                                .height(14.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(shimmerBrush),
                        )
                        Spacer(Modifier.height(8.dp))
                        Box(
                            Modifier
                                .fillMaxWidth(0.45f)
                                .height(10.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(shimmerBrush),
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
                colors = ButtonDefaults.buttonColors(
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
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "card_scale",
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { pressed = true; onClick() }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
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
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .align(Alignment.BottomCenter)
                        .background(GradientCyanDark),
                )
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
                        drama.tags.take(3).forEach { tag ->
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
