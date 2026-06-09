package com.qingmo.app.ui.screens

import android.content.Context
import android.graphics.Canvas
import android.util.Log
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.util.SparseArray
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.qingmo.app.data.ProgressCache
import com.qingmo.app.data.api.RetrofitClient
import com.qingmo.app.data.model.DramaBrief
import com.qingmo.app.data.model.DramaDetail
import com.qingmo.app.data.model.EpisodeBrief
import com.qingmo.app.data.repository.DramaRepository
import com.qingmo.app.ui.theme.Background
import com.qingmo.app.ui.theme.Border
import com.qingmo.app.ui.theme.OnBackground
import com.qingmo.app.ui.theme.OnSurface
import com.qingmo.app.ui.theme.OnSurfaceVariant
import com.qingmo.app.ui.theme.PlayerAccent
import com.qingmo.app.ui.theme.Primary
import com.qingmo.app.ui.theme.SurfaceVariant
import com.qingmo.app.xiaomo.XiaoMoCore
import com.qingmo.app.xiaomo.ui.XiaoMoPanelView
import com.qingmo.app.xiaomo.ui.XiaoMoPeekView
import com.qingmo.app.xiaomo.ui.XiaoMoChatPanel
import com.qingmo.app.ui.components.OverlayInput
import com.qingmo.app.ui.components.DanmakuView
import com.qingmo.app.ui.components.DanmakuItem
import com.qingmo.app.data.user.DeviceIdProvider
import android.widget.Toast
import android.graphics.Color as AndroidColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val C_WHITE = -0x1
private const val C_BLACK = -0x1000000
private const val C_WHITE50 = 0x80FFFFFF.toInt()
private const val C_WHITE70 = 0xB3FFFFFF.toInt()
private const val C_WHITE85 = 0xD9FFFFFF.toInt()
private const val C_WHITE20 = 0x33FFFFFF
private const val C_DARK_BG = 0xFF18181A.toInt()

@Suppress("ktlint:standard:function-naming")
@Composable
fun DramaPagerScreen(
    dramaId: Int,
    episodeId: Long,
    onBack: () -> Unit,
    onNextDrama: (Int) -> Unit = {},
    onCurrentEpisodeChanged: (Long) -> Unit = {},
    onGoDetail: () -> Unit = {},
) {
    val repo = remember { DramaRepository() }
    var detail by remember { mutableStateOf<DramaDetail?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var allDramas by remember { mutableStateOf<List<DramaBrief>>(emptyList()) }
    var retryTrigger by remember { mutableStateOf(0) }
    LaunchedEffect(dramaId, retryTrigger) { repo.getDramaDetail(dramaId).onSuccess { detail = it }.onFailure { err = it.message } }
    LaunchedEffect(Unit) { repo.getDramas().onSuccess { allDramas = it } }
    val resolved =
        remember(episodeId, detail) {
            if (episodeId == -1L &&
                detail != null
            ) {
                detail!!.episodes.minByOrNull { it.episodeNum }?.episodeId ?: episodeId
            } else {
                episodeId
            }
        }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            err != null -> Err(err!!, onRetry = { retryTrigger++ })
            detail == null -> Load()
            detail!!.episodes.isEmpty() -> Err("\u6682\u65E0\u5267\u96C6\u6570\u636E")
            else -> Pager(detail!!, resolved, allDramas, repo, onBack, onNextDrama, onCurrentEpisodeChanged, onGoDetail)
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun Pager(
    detail: DramaDetail,
    startId: Long,
    allDramas: List<DramaBrief>,
    repo: DramaRepository,
    onBack: () -> Unit,
    onNextDrama: (Int) -> Unit,
    onCurrentEpisodeChanged: (Long) -> Unit,
    onGoDetail: () -> Unit,
) {
    val ctx = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current.density
    val dpx: (Float) -> Int = { (it * density + 0.5f).toInt() }
    val sorted = remember(detail.episodes) { detail.episodes.sortedBy { it.episodeNum } }
    val startIdx =
        remember(startId, sorted) { sorted.indexOfFirst { it.episodeId == startId }.coerceIn(0, sorted.lastIndex) }
    var curPage by remember { mutableIntStateOf(startIdx) }
    var danmaku by remember { mutableStateOf(true) }
    var lastDanmakuToggleTime by remember { mutableLongStateOf(0L) }
    var rate by remember { mutableFloatStateOf(1.0f) }
    var showEps by remember { mutableStateOf(false) }
    var showSpd by remember { mutableStateOf(false) }
    var showXiaoMo by remember { mutableStateOf(false) }
    var xiaoMoExpanded by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }
    var curPos by remember { mutableLongStateOf(0L) }
    var curDur by remember { mutableLongStateOf(0L) }
    val userId = remember { com.qingmo.app.data.auth.TokenManager.getUserId().takeIf { it > 0 }?.toString() ?: DeviceIdProvider.getDeviceId(ctx) }
    val keyboardController = LocalSoftwareKeyboardController.current
    var showDanmakuInput by remember { mutableStateOf(false) }
    var showCommentInput by remember { mutableStateOf(false) }
    var showDiscussionSheetEpisodeId by remember { mutableLongStateOf(-1L) }
    var danmakuSentText by remember { mutableStateOf<String?>(null) }
    var showNoteInput by remember { mutableStateOf(false) }
    var showBranchVote by remember { mutableStateOf(false) }
    var showCharacterChat by remember { mutableStateOf(false) }
    var vp2 by remember { mutableStateOf<ViewPager2?>(null) }
    val curEp = remember(curPage, sorted) { sorted.getOrNull(curPage) }
    val dramas = remember(allDramas) { allDramas.sortedBy { it.id } }
    val displayMetrics = ctx.resources.displayMetrics
    val prevDrama = {
        val di = dramas.indexOfFirst { it.id == detail.id }
        if (di > 0) onNextDrama(dramas[di - 1].id)
    }
    val adapter =
        remember {
            NativeAdapter(
                ctx,
                displayMetrics,
                sorted,
                detail,
                repo,
                curPage,
                rate,
                danmaku,
                { onBack() },
                { showSpd = true },
                { np ->
                    if (np < sorted.size) {
                        vp2?.setCurrentItem(np, true)
                    } else {
                        val di = dramas.indexOfFirst { it.id == detail.id }
                        if (di >= 0 &&
                            di + 1 < dramas.size
                        ) {
                            onNextDrama(dramas[di + 1].id)
                        }
                    }
                },
                { showEps = true },
                onGoDetail,
                prevDrama,
            ).also {
                it.onFullscreenChange = { fs -> fullscreen = fs }
                it.onPlayerTap = {
                    if (showDanmakuInput || showCommentInput) {
                        showDanmakuInput = false
                        showCommentInput = false
                    } else {
                        it.activePlayer?.playWhenReady = !(it.activePlayer?.playWhenReady ?: true)
                    }
                }
                it.onLikeClick = { episodeId ->
                    coroutineScope.launch(Dispatchers.IO) {
                        try { RetrofitClient.api.toggleLike(episodeId, mapOf("user_id" to userId)) }
                        catch (_: Exception) {}
                    }
                }
                it.onFavoriteClick = { dramaId ->
                    coroutineScope.launch(Dispatchers.IO) {
                        try { RetrofitClient.api.toggleFavorite(dramaId.toInt(), mapOf("user_id" to userId)) }
                        catch (_: Exception) {}
                    }
                }
                it.onCommentClick = { epId ->
                    showDiscussionSheetEpisodeId = epId
                    showCommentInput = true
                }
                it.onDanmakuClick = { showDanmakuInput = true }
                it.userId = userId
            }
        }
    LaunchedEffect(Unit) {
        while (true) {
            val p = adapter.activePlayer
            if (p !=
                null
            ) {
                curPos = p.currentPosition
                curDur =
                    if (p.duration >
                        0
                    ) {
                        p.duration
                    } else {
                        curDur
                    }
                ; val pr =
                    if (curDur >
                        0
                    ) {
                        curPos.toFloat() / curDur
                    } else {
                        0f
                    }
                ; adapter.updateProgressBar(pr, curDur)
            }
            delay(200)
        }
    }
    LaunchedEffect(fullscreen) { adapter.setFullscreen(fullscreen) }
    DisposableEffect(Unit) { onDispose { adapter.releaseAll() } }
    LaunchedEffect(curPage) { curEp?.let { onCurrentEpisodeChanged(it.episodeId) } }

    // 小墨生命周期：进入播放器 → Peek 状态，退出 → Hidden
    DisposableEffect(Unit) {
        XiaoMoCore.onEnterPlayer()
        showXiaoMo = true
        onDispose {
            XiaoMoCore.onExitPlayer()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { c ->
            ViewPager2(c).apply {
                orientation = ViewPager2.ORIENTATION_VERTICAL
                offscreenPageLimit =
                    1
                this.adapter = adapter
                setCurrentItem(startIdx, false)
                var downY = 0f
                setOnTouchListener { _, event ->
                    when (event.action) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            downY = event.y
                            false
                        }
                        android.view.MotionEvent.ACTION_UP -> {
                            val dy = downY - event.y
                            val threshold = dpx(80f)
                            if (curPage == sorted.size - 1 && dy > threshold) {
                                val di = dramas.indexOfFirst { it.id == detail.id }
                                if (di >= 0 && di + 1 < dramas.size) onNextDrama(dramas[di + 1].id)
                                true
                            } else if (curPage == 0 && dy < -threshold) {
                                prevDrama()
                                true
                            } else {
                                false
                            }
                        }
                        else -> false
                    }
                }
                registerOnPageChangeCallback(
                    object : ViewPager2.OnPageChangeCallback() {
                        override fun onPageSelected(p: Int) {
                            adapter.setActive(p)
                            curPage = p
                        }
                    },
                )
                post { adapter.setActive(startIdx) }
                adapter.viewPager2 = this
                vp2 = this
            }
        }, modifier = Modifier.fillMaxSize())
        if (showEps) {
            ESheet(sorted, curEp?.episodeId ?: startId, sorted.size, { ep ->
                showEps = false
                val i =
                    sorted.indexOfFirst {
                        it.episodeId ==
                            ep.episodeId
                    }
                ; if (i >=
                    0
                ) {
                    adapter.preparePlayer(i)
                    vp2?.post { vp2?.setCurrentItem(i, false) }
                }
            }, { showEps = false })
        }
        if (showSpd) {
            SS(rate, {
                rate = it
                adapter.setRate(it)
                showSpd = false
            }, { showSpd = false })
        }

        // 小墨 Peek — 右侧栏上方
        if (!fullscreen && showXiaoMo && !xiaoMoExpanded) {
            val xiaoMoState by XiaoMoCore.state.collectAsState()
            XiaoMoPeekView(
                visible = true,
                pose = xiaoMoState.pose,
                emotion = xiaoMoState.emotion,
                onClick = { xiaoMoExpanded = true },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(bottom = 240.dp),
            )

            // 一键弹幕浮层：高光点触发后，在小墨下方弹出互动面板
            val pendingHL = xiaoMoState.pendingDanmakuHighlight
            if (pendingHL != null) {
                val scope = rememberCoroutineScope()
                var showMode by remember { mutableStateOf("emotion") } // emotion | vote | quiz
                var voteData by remember { mutableStateOf<Map<String, Any>?>(null) }
                var quizData by remember { mutableStateOf<Map<String, Any>?>(null) }
                var voteCounts by remember { mutableStateOf(mapOf("a" to 0, "b" to 0)) }
                var myVote by remember { mutableStateOf<String?>(null) }
                var quizSelected by remember { mutableStateOf<String?>(null) }
                var quizResult by remember { mutableStateOf<String?>(null) }

                // 加载投票/问答数据
                LaunchedEffect(pendingHL.id) {
                    // 检查投票
                    kotlinx.coroutines.withContext(Dispatchers.IO) {
                        try {
                            val v = RetrofitClient.api.getHighlightVote(pendingHL.id, userId)
                            if (v["vote"] != null) {
                                voteData = (v["vote"] as? Map<String, Any>)
                                voteCounts = ((v["vote"] as? Map<String, Any>)?.get("counts") as? Map<*, *>)?.mapKeys { it.key.toString() }?.mapValues { (it.value as? Number)?.toInt() ?: 0 } ?: mapOf("a" to 0, "b" to 0)
                                myVote = (v["vote"] as? Map<String, Any>)?.get("my_choice") as? String
                                showMode = "vote"
                            }
                        } catch (_: Exception) {}
                    }
                    // 检查问答
                    kotlinx.coroutines.withContext(Dispatchers.IO) {
                        try {
                            val q = RetrofitClient.api.getHighlightQuiz(pendingHL.id)
                            if (q["quiz"] != null) {
                                quizData = q["quiz"] as? Map<String, Any>
                                showMode = "quiz"
                            }
                        } catch (_: Exception) {}
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(bottom = 140.dp, end = 4.dp),
                ) {
                    when (showMode) {
                        "vote" -> {
                            val vd = voteData
                            if (vd != null) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .background(Color(0xFF2A2A2A).copy(alpha = 0.88f), RoundedCornerShape(16.dp))
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                ) {
                                    Text("📊 ${vd["question"] ?: ""}", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.height(10.dp))
                                    val total = (voteCounts["a"] ?: 0) + (voteCounts["b"] ?: 0)
                                    listOf("a" to (vd["option_a"] as? String ?: "A"), "b" to (vd["option_b"] as? String ?: "B")).forEach { (key, label) ->
                                        val count = voteCounts[key] ?: 0
                                        val pct = if (total > 0) (count.toFloat() / total) else 0f
                                        val isMy = myVote == key
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isMy) Color(0xFF7C4DFF).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.12f),
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                                .clickable(enabled = myVote == null) {
                                                    myVote = key
                                                    scope.launch(Dispatchers.IO) {
                                                        try {
                                                            val resp = RetrofitClient.api.castHighlightVote(pendingHL.id, mapOf("user_id" to userId, "choice" to key))
                                                            val newCounts = resp["counts"] as? Map<*, *>
                                                            if (newCounts != null) {
                                                                voteCounts = newCounts.mapKeys { it.key.toString() }.mapValues { (it.value as? Number)?.toInt() ?: 0 }
                                                            }
                                                        } catch (_: Exception) {}
                                                    }
                                                },
                                        ) {
                                            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text(label, fontSize = 14.sp, color = Color.White)
                                                if (myVote != null) Text("${(pct * 100).toInt()}%", fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        "quiz" -> {
                            val qd = quizData
                            if (qd != null) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .background(Color(0xFF2A2A2A).copy(alpha = 0.88f), RoundedCornerShape(16.dp))
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                ) {
                                    Text("🤔 ${qd["question"] ?: ""}", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.height(8.dp))
                                    listOf("A", "B", "C", "D").forEach { key ->
                                        val label = qd[key] as? String ?: return@forEach
                                        val isSel = quizSelected == key
                                        val isCorrect = key == (qd["answer"] as? String ?: "")
                                        val bgColor = when {
                                            quizSelected == null -> Color.White.copy(alpha = 0.12f)
                                            isSel && isCorrect -> Color(0xFF4CAF50).copy(alpha = 0.6f)
                                            isSel && !isCorrect -> Color(0xFFE53935).copy(alpha = 0.5f)
                                            !isSel && isCorrect && quizSelected != null -> Color(0xFF4CAF50).copy(alpha = 0.3f)
                                            else -> Color.White.copy(alpha = 0.08f)
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = bgColor,
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                                .clickable(enabled = quizSelected == null) {
                                                    quizSelected = key
                                                    val correct = key == (qd["answer"] as? String ?: "")
                                                    quizResult = if (correct) "✅ 答对了！" else "❌ 正确答案是 ${qd["answer"]}"
                                                    scope.launch(Dispatchers.IO) {
                                                        try {
                                                            RetrofitClient.api.castHighlightVote(pendingHL.id, mapOf("user_id" to userId, "choice" to key))
                                                        } catch (_: Exception) {}
                                                    }
                                                },
                                        ) {
                                            Text("$key. $label", fontSize = 13.sp, color = Color.White, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
                                        }
                                    }
                                    quizResult?.let {
                                        Spacer(Modifier.height(6.dp))
                                        Text(it, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        else -> {
                            // 默认情绪弹幕面板
                            val hints = pendingHL.emotionHints ?: listOf("👍", "❤️", "😂", "😮", "👏")
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .background(Color(0xFF2A2A2A).copy(alpha = 0.88f), RoundedCornerShape(16.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            ) {
                                Text(
                                    text = pendingHL.title,
                                    fontSize = 13.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    hints.forEach { hint ->
                                        Surface(
                                            shape = RoundedCornerShape(20.dp),
                                            color = Color.White.copy(alpha = 0.15f),
                                            modifier = Modifier.clickable {
                                                val ep = curEp ?: return@clickable
                                                val text = hint
                                                val nowSec = curPos / 1000f
                                                val item = DanmakuItem(
                                                    id = System.currentTimeMillis(),
                                                    text = text,
                                                    timeSec = nowSec,
                                                    color = AndroidColor.WHITE,
                                                    userId = userId,
                                                )
                                                adapter.addDanmakuToCurrent(ep.episodeId, item, forceEmit = true)
                                                scope.launch(Dispatchers.IO) {
                                                    try {
                                                        RetrofitClient.api.postDanmaku(
                                                            mapOf(
                                                                "user_id" to userId,
                                                                "episode_id" to ep.episodeId,
                                                                "text" to text,
                                                                "time_sec" to nowSec.toDouble(),
                                                            )
                                                        )
                                                    } catch (_: Exception) {}
                                                }
                                                adapter.onUserDanmaku(ep.episodeId, text)
                                                XiaoMoCore.onDanmakuSentSuccess()
                                            },
                                        ) {
                                            Text(
                                                text = hint,
                                                fontSize = 22.sp,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 小墨聊天底部抽屉 — 和评论界面完全一致风格
        XiaoMoChatSheet(
            visible = xiaoMoExpanded,
            userId = userId,
            dramaContext = curEp?.let { mapOf("drama_id" to detail.id, "drama_title" to detail.title, "episode_num" to it.episodeNum) },
            onDismiss = { xiaoMoExpanded = false; XiaoMoCore.collapse() }
        )

        // 弹幕输入浮层
        LaunchedEffect(showDanmakuInput, showCommentInput) {
            globalAnyInputVisible = showDanmakuInput || showCommentInput
        }
        OverlayInput(
            visible = showDanmakuInput,
            placeholder = "发一条弹幕...",
            danmakuVisible = danmaku,
            onDanmakuToggle = {
                val now = System.currentTimeMillis()
                if (now - lastDanmakuToggleTime > 300) {
                    lastDanmakuToggleTime = now
                    danmaku = !danmaku
                    adapter.setDanmakuEnabled(danmaku)
                    Toast.makeText(ctx, if (danmaku) "弹幕已开启" else "弹幕已隐藏", Toast.LENGTH_SHORT).show()
                }
            },
            maxLength = 40,
            onSend = { text ->
                val ep = curEp
                if (ep == null) {
                    Toast.makeText(ctx, "请先选择剧集", Toast.LENGTH_SHORT).show()
                    keyboardController?.hide()
                } else {
                    // 即时本地渲染：先飘屏，再异步发请求，避免时间窗口过期
                    val nowSec = curPos / 1000f
                    val item = DanmakuItem(id = System.currentTimeMillis(), text = text, timeSec = nowSec, color = AndroidColor.WHITE, userId = userId)
                    adapter.addDanmakuToCurrent(ep.episodeId, item, forceEmit = true)
                    danmakuSentText = "弹幕已发送"
                    adapter.onUserDanmaku(ep.episodeId, text)
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            RetrofitClient.api.postDanmaku(mapOf("user_id" to userId, "episode_id" to ep.episodeId, "text" to text, "time_sec" to nowSec))
                        } catch (_: Exception) { }
                    }
                    keyboardController?.hide()
                }
            },
            onDismiss = { showDanmakuInput = false },
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // 讨论完整弹窗 — 从持久化Auth取真实当前登录用户ID
        DiscussionSheet(
            visible = showCommentInput,
            episodeId = showDiscussionSheetEpisodeId,
            userId = com.qingmo.app.data.auth.TokenManager.getUserId().toString(),
            scope = rememberCoroutineScope(),
            onDismiss = { showCommentInput = false },
            onCommentPosted = { adapter.refreshCommentCount(showDiscussionSheetEpisodeId) },
            onNavigateToDrama = { onBack() }
        )

        // 弹幕发送成功提示 — 播放器上半区居中
        if (danmakuSentText != null) {
            var visible by remember { mutableStateOf(true) }
            val alpha by animateFloatAsState(
                targetValue = if (visible) 1f else 0f,
                animationSpec = tween(300),
                finishedListener = { if (!visible) danmakuSentText = null }
            )
            LaunchedEffect(danmakuSentText) {
                delay(1200L)
                visible = false
            }
            Text(
                text = danmakuSentText ?: "",
                color = Color(0xFF1A535C),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 120.dp)
                    .alpha(alpha)
                    .background(Color(0xFFFAF8F0).copy(alpha = alpha), RoundedCornerShape(8.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
        // 追剧笔记 📝 浮动按钮
        if (!fullscreen) {
            Box(
                Modifier.align(Alignment.CenterEnd).padding(bottom = 320.dp, end = 12.dp)
                    .size(40.dp).background(Color(0xFF2A2A2A).copy(alpha = 0.7f), CircleShape)
                    .clickable { showNoteInput = true },
                contentAlignment = Alignment.Center,
            ) { Text("📝", fontSize = 18.sp) }
        }
        // 分支投票 🎬 浮动按钮
        if (!fullscreen) {
            Box(
                Modifier.align(Alignment.CenterEnd).padding(bottom = 370.dp, end = 12.dp)
                    .size(40.dp).background(Color(0xFF2A2A2A).copy(alpha = 0.7f), CircleShape)
                    .clickable { showBranchVote = true },
                contentAlignment = Alignment.Center,
            ) { Text("🎬", fontSize = 18.sp) }
        }
        // 角色聊天浮动按钮
        if (!fullscreen) {
            Box(
                Modifier.align(Alignment.CenterEnd).padding(bottom = 420.dp, end = 12.dp)
                    .size(40.dp).background(Color(0xFF7C4DFF).copy(alpha = 0.8f), CircleShape)
                    .clickable { showCharacterChat = true },
                contentAlignment = Alignment.Center,
            ) { Text("🎭", fontSize = 18.sp) }
        }
    }

    // 追剧笔记弹窗
    if (showNoteInput) {
        var noteText by remember { mutableStateOf("") }
        val scope = rememberCoroutineScope()
        val ep = curEp
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { showNoteInput = false }) {
            Surface(Modifier.fillMaxWidth().padding(32.dp).align(Alignment.Center), shape = RoundedCornerShape(20.dp), color = Color.White) {
                Column(Modifier.padding(20.dp)) {
                    Text("📝 追剧笔记", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF333333))
                    if (ep != null) Text("第${ep.episodeNum}集 · ${formatSec(curPos / 1000)}", fontSize = 12.sp, color = Color(0xFF999999), modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(12.dp))
                    androidx.compose.foundation.text.BasicTextField(
                        value = noteText,
                        onValueChange = { if (it.length <= 300) noteText = it },
                        modifier = Modifier.fillMaxWidth().height(100.dp).background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp)).padding(12.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF333333), fontSize = 14.sp),
                        decorationBox = { inner ->
                            Box { if (noteText.isEmpty()) Text("记下此刻的想法...", color = Color(0xFFBBBBBB), fontSize = 14.sp); inner() }
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showNoteInput = false }) { Text("取消", color = Color(0xFF999999)) }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = {
                            if (noteText.trim().isEmpty() || ep == null) return@TextButton
                            scope.launch(Dispatchers.IO) {
                                try {
                                    RetrofitClient.api.createNote(ep.episodeId, mapOf(
                                        "user_id" to userId, "text" to noteText.trim(),
                                        "time_sec" to (curPos / 1000.0), "drama_id" to detail.id,
                                    ))
                                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                                        showNoteInput = false; noteText = ""
                                        Toast.makeText(ctx, "笔记已保存", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (_: Exception) {
                                    kotlinx.coroutines.withContext(Dispatchers.Main) { Toast.makeText(ctx, "保存失败", Toast.LENGTH_SHORT).show() }
                                }
                            }
                        }, enabled = noteText.trim().isNotEmpty()) {
                            Text("保存", color = Color(0xFF1E88E5), fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }

    // 分支投票弹窗
    if (showBranchVote) {
        var bvData by remember { mutableStateOf<Map<String, Any>?>(null) }
        var bvCounts by remember { mutableStateOf(mapOf("a" to 0, "b" to 0)) }
        var bvChoice by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        LaunchedEffect(showBranchVote) {
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                try {
                    val v = RetrofitClient.api.getBranchVote(detail.id.toInt(), userId)
                    bvData = v["vote"] as? Map<String, Any>
                    bvCounts = ((v["vote"] as? Map<String, Any>)?.get("counts") as? Map<*, *>)?.mapKeys { it.key.toString() }?.mapValues { (it.value as? Number)?.toInt() ?: 0 } ?: mapOf("a" to 0, "b" to 0)
                    bvChoice = (v["vote"] as? Map<String, Any>)?.get("my_choice") as? String
                } catch (_: Exception) { showBranchVote = false }
            }
        }
        val bd = bvData
        if (bd != null) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { showBranchVote = false }) {
                Surface(Modifier.fillMaxWidth().padding(32.dp).align(Alignment.Center), shape = RoundedCornerShape(20.dp), color = Color.White) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎬 剧情分支投票", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF333333))
                        Spacer(Modifier.height(8.dp))
                        Text(bd["question"] as? String ?: "", fontSize = 14.sp, color = Color(0xFF555555), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        val total = (bvCounts["a"] ?: 0) + (bvCounts["b"] ?: 0)
                        listOf("a" to (bd["option_a"] as? String ?: "A"), "b" to (bd["option_b"] as? String ?: "B")).forEach { (key, label) ->
                            val count = bvCounts[key] ?: 0
                            val pct = if (total > 0) (count.toFloat() / total * 100).toInt() else 0
                            val isMy = bvChoice == key
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isMy) Color(0xFF7C4DFF).copy(alpha = 0.15f) else Color(0xFFF5F5F5),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                    .clickable(enabled = bvChoice == null && !(bd["expired"] as? Boolean ?: false)) {
                                        bvChoice = key
                                        scope.launch(Dispatchers.IO) {
                                            try {
                                                val resp = RetrofitClient.api.castBranchVote(detail.id.toInt(), mapOf("user_id" to userId, "choice" to key))
                                                val nc = resp["counts"] as? Map<*, *>
                                                if (nc != null) bvCounts = nc.mapKeys { it.key.toString() }.mapValues { (it.value as? Number)?.toInt() ?: 0 }
                                            } catch (_: Exception) {}
                                        }
                                    },
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF333333))
                                    if (bvChoice != null) Text("$pct% (${count}票)", fontSize = 12.sp, color = Color(0xFF999999), modifier = Modifier.padding(top = 2.dp))
                                }
                            }
                        }
                        if (bd["expired"] as? Boolean ?: false) {
                            Spacer(Modifier.height(8.dp)); Text("投票已截止", fontSize = 12.sp, color = Color(0xFFE53935))
                        }
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { showBranchVote = false }) { Text("关闭", color = Color(0xFF999999)) }
                    }
                }
            }
        }
    }

    // 角色AI对话 (inline)
    if (showCharacterChat) {
        var charList by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
        var selChar by remember { mutableStateOf<Map<String, Any>?>(null) }
        var charInput by remember { mutableStateOf("") }
        var charMsgs by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
        var charLoading by remember { mutableStateOf(false) }
        val charScope = rememberCoroutineScope()
        LaunchedEffect(detail.id) {
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                try { charList = RetrofitClient.api.listCharacters(detail.id.toInt()) } catch (_: Exception) {}
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable { if (selChar == null) showCharacterChat = false }) {
            Surface(Modifier.fillMaxWidth().fillMaxHeight(0.75f).align(Alignment.BottomCenter), shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), color = Color.White) {
                Column(Modifier.fillMaxSize().imePadding()) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (selChar != null) "💬 ${(selChar!!["name"] ?: "")}" else "🎭 选择角色", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { if (selChar != null) selChar = null else showCharacterChat = false }) {
                            Text(if (selChar != null) "返回" else "关闭", color = Color(0xFF999999))
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color(0xFFEEEEEE)))
                    if (selChar != null) {
                        val c = selChar!!
                        androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), reverseLayout = true) {
                            val rev = charMsgs.reversed()
                            items(rev.size) { idx -> val msg = rev[idx]; val isUser = msg.first == "user"
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
                                    Surface(shape = RoundedCornerShape(12.dp), color = if (isUser) Color(0xFF1E88E5) else Color(0xFFF0F0F0)) {
                                        androidx.compose.material3.Text(msg.second, fontSize = 14.sp, color = if (isUser) Color.White else Color(0xFF333333), modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                                    }
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(12.dp).background(Color.White), verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.foundation.text.BasicTextField(value = charInput, onValueChange = { charInput = it }, modifier = Modifier.weight(1f).height(40.dp).background(Color(0xFFF5F5F5), RoundedCornerShape(20.dp)).padding(horizontal = 12.dp), textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF333333), fontSize = 14.sp), decorationBox = { ib -> Box(contentAlignment = Alignment.CenterStart) { if (charInput.isEmpty()) Text("和${c["name"]}说点什么...", color = Color(0xFFBBBBBB), fontSize = 14.sp); ib() } })
                            Spacer(Modifier.width(8.dp))
                            Text("发送", color = if (charInput.isNotBlank() && !charLoading) Color(0xFF1E88E5) else Color(0xFFBBBBBB), fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clickable(enabled = charInput.isNotBlank() && !charLoading) {
                                val msg = charInput.trim(); if (msg.isEmpty()) return@clickable; charMsgs = charMsgs + ("user" to msg); charInput = ""; charLoading = true
                                charScope.launch(Dispatchers.IO) {
                                    try {
                                        val cid = (c["id"] as? Number)?.toInt() ?: return@launch
                                        val r = RetrofitClient.api.characterChat(cid, mapOf("user_message" to msg, "drama_id" to detail.id))
                                        charMsgs = charMsgs + ("char" to (r["reply"] as? String ?: "……"))
                                    } catch (_: Exception) { charMsgs = charMsgs + ("char" to "（连接失败）") }
                                    charLoading = false
                                }
                            })
                        }
                    } else {
                        androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            items(charList.size) { idx -> val c = charList[idx]
                                Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFF8F8F8), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { selChar = c }) {
                                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Surface(shape = CircleShape, color = Color(0xFF7C4DFF), modifier = Modifier.size(44.dp)) { Box(contentAlignment = Alignment.Center) { androidx.compose.material3.Text((c["name"] as? String ?: "?").first().uppercase(), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) } }
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            androidx.compose.material3.Text(c["name"] as? String ?: "", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF333333))
                                            val role = c["role"] as? String ?: ""; if (role.isNotEmpty()) androidx.compose.material3.Text(role, fontSize = 12.sp, color = Color(0xFF999999))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatSec(s: Long): String = "${s / 60}:${(s % 60).toString().padStart(2, '0')}"

private fun formatCount(n: Int): String = when {
    n >= 10000 -> "${n / 10000}.${(n % 10000) / 1000}w"
    n > 0 -> n.toString()
    else -> "0"
}

private class NativeAdapter(
    private val ctx: Context,
    private val dm: android.util.DisplayMetrics,
    private val eps: List<EpisodeBrief>,
    private val detail: DramaDetail,
    private val repo: DramaRepository,
    private var cur: Int,
    private var rate: Float,
    private var danmaku: Boolean,
    private val onBack: () -> Unit,
    private val onSpeed: () -> Unit,
    private val onEpisodeEnd: (Int) -> Unit,
    private val onShowEps: () -> Unit,
    private val onGoDetail: () -> Unit,
    private val onPrevDrama: () -> Unit,
    var onFullscreenChange: ((Boolean) -> Unit)? = null,
    var onLikeClick: ((Long) -> Unit)? = null,
    var onCommentClick: ((Long) -> Unit)? = null,
    var onFavoriteClick: ((Long) -> Unit)? = null,
    var onDanmakuClick: (() -> Unit)? = null,
    var userId: String = "",
) : RecyclerView.Adapter<NativeAdapter.VH>() {
    val players = mutableMapOf<Int, ExoPlayer>()
    var activePlayer: ExoPlayer? = null
    var viewPager2: ViewPager2? = null // Reference for touch disallow during seekbar drag
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val progressJobs = mutableMapOf<Int, Job>()
    private val viewHolders = SparseArray<VH>()
    private val d = dm.density
    private var danmakuGlobalEnabled = true
    private val highlightCache = mutableMapOf<Int, List<com.qingmo.app.data.model.HighlightItem>>()
    private val triggeredSet = mutableSetOf<Int>()
    private var consecutiveCount = 1
    private var lastWatchedDramaId: Long = 0

    fun setDanmakuEnabled(enabled: Boolean) {
        danmakuGlobalEnabled = enabled
        for (i in 0 until viewHolders.size()) {
            viewHolders.valueAt(i).danmakuView.setDanmakuEnabled(enabled)
        }
    }

    fun refreshCommentCount(episodeId: Long) {
        scope.launch(Dispatchers.IO) {
            try {
                val resp = RetrofitClient.api.getEpisodeCounts(episodeId, userId)
                val commentCnt = (resp["comment_count"] as? Number)?.toInt() ?: 0
                val label = activeVh?.commentLabel
                label?.post { label.text = formatCount(commentCnt) }
            } catch (_: Exception) {}
        }
    }

    private fun dp(v: Float) = (v * d + 0.5f).toInt()

    internal var activeVh: VH? = null
    private var isFullscreen = false
    private val danmakuCache = mutableMapOf<Long, List<DanmakuItem>>()
    private val danmakuLoaded = mutableSetOf<Long>()
    private val danmakuDataSetTriggered = mutableSetOf<Long>() // 弹幕数据已经给DanmakuView设置过一次，永远不再重复调用
    private val screenH = dm.heightPixels
    private val statusBarH: Int =
        run {
            val resid = ctx.resources.getIdentifier("status_bar_height", "dimen", "android")
            if (resid > 0) ctx.resources.getDimensionPixelSize(resid) else dp(24f)
        }
    private val navBarH: Int =
        run {
            val resid = ctx.resources.getIdentifier("navigation_bar_height", "dimen", "android")
            if (resid > 0) ctx.resources.getDimensionPixelSize(resid) else dp(48f)
        }
    private val topGradH = (screenH * 0.22f).toInt() + statusBarH
    private val botGradH = (screenH * 0.36f).toInt()

    var onPlayerTap: (() -> Unit)? = null
    class VH(
        val root: FrameLayout,
        val pv: PlayerView,
        val danmakuView: DanmakuView,
        val titleTv: TextView,
        val speedTv: TextView,
        val descTv: TextView,
        val danmakuBtn: ImageView,
        val seekBar: SeekBar,
        val timeLabel: TextView,
        val peekLabel: TextView,
        val topBar: View,
        val rightBar: View,
        val bottomInfo: View,
        val likeLabel: TextView,
        val commentLabel: TextView,
        val favoriteLabel: TextView,
        var likeIv: ImageView,
        var isLiked: Boolean = false,
        var likeCount: Int = 0,
        var countsLoaded: Boolean = false,
        var favoriteIv: ImageView,
        var isFavorited: Boolean = false,
        var favoritesLoaded: Boolean = false,
    ) : RecyclerView.ViewHolder(root)

    override fun getItemCount() = eps.size

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): VH {
        val c = ctx
        val r =
            FrameLayout(c).apply {
                layoutParams = ViewGroup.LayoutParams(-1, -1)
                setBackgroundColor(C_BLACK)
            }
        val pv =
            PlayerView(c).apply {
                useController = false
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                layoutParams = FrameLayout.LayoutParams(-1, -1).apply {
                    bottomMargin = dp(52f)
                }
            }
        val playIcon =
            ImageView(c).apply {
                setImageResource(com.qingmo.app.R.drawable.play_icon)
                scaleType =
                    ImageView.ScaleType.FIT_CENTER
                layoutParams = FrameLayout.LayoutParams(dp(48f), dp(48f), Gravity.CENTER)
                visibility =
                    View.GONE
            }
        pv.setOnClickListener {
            onPlayerTap?.invoke()
        }
        r.addView(pv)
        r.addView(playIcon)
        r.addView(
            View(c).apply {
                background =
                    GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x80000000.toInt(), 0))
                ; layoutParams =
                    FrameLayout.LayoutParams(-1, topGradH, Gravity.TOP)
            },
        )
        r.addView(
            View(c).apply {
                background =
                    GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(0xBF000000.toInt(), 0))
                ; layoutParams =
                    FrameLayout.LayoutParams(-1, botGradH, Gravity.BOTTOM)
            },
        )
        val danmakuView = DanmakuView(c).apply {
            setDanmakuEnabled(danmakuGlobalEnabled)
            layoutParams = FrameLayout.LayoutParams(-1, -1).apply {
                topMargin = statusBarH + dp(44f)
                bottomMargin = dp(52f)
            }
        }
        r.addView(danmakuView)
        val tb =
            LinearLayout(c).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16f), dp(10f), dp(16f), dp(10f))
                layoutParams =
                    FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply {
                        topMargin = statusBarH
                    }
            }
        r.addView(tb)
        tb.addView(
            makeTv("\u2190", 22f, C_WHITE).apply { setOnClickListener { onBack() } },
            LinearLayout.LayoutParams(-2, -2),
        )
        tb.addView(Space(c).apply { layoutParams = LinearLayout.LayoutParams(dp(8f), 0) })
        val titleTv = makeTv("\u7B2C1\u96C6", 18f, C_WHITE, true)
        tb.addView(titleTv, LinearLayout.LayoutParams(0, -2, 1f))
        val speedTv =
            makeTv("1.0x", 16f, C_WHITE85).apply {
                setOnClickListener { onSpeed() }
                setPadding(dp(8f), 0, dp(8f), 0)
            }
        tb.addView(speedTv, LinearLayout.LayoutParams(-2, -2))
        tb.addView(Space(c).apply { layoutParams = LinearLayout.LayoutParams(dp(8f), 0) })
        tb.addView(makeTv("\u22EE", 20f, C_WHITE85), LinearLayout.LayoutParams(-2, -2))
        val rb =
            LinearLayout(c).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams =
                    FrameLayout
                        .LayoutParams(
                            -2,
                            -2,
                            Gravity.END or Gravity.BOTTOM,
                        ).apply { setMargins(0, 0, dp(0f), dp(130f)) }
            }
        r.addView(rb)
        fun formatCount(n: Int): String {
            return when {
                n >= 10000 -> "%.1fW".format(n / 10000f)
                n >= 1000 -> "%.1fK".format(n / 1000f)
                else -> n.toString()
            }
        }
        var favoriteIv = ImageView(c).apply {
            setImageResource(com.qingmo.app.R.drawable.unstarred)
            adjustViewBounds = true
        }
        val favoriteLabel = makeTv("", 11f, C_WHITE85).apply { gravity = Gravity.CENTER; text = formatCount(0) }
        val favoriteContainer = FrameLayout(c).apply {
            setPadding(dp(0f), dp(0f), dp(0f), dp(0f))
            val lp = FrameLayout.LayoutParams(dp(64f), dp(64f), Gravity.CENTER)
            favoriteIv.layoutParams = lp
            addView(favoriteIv, FrameLayout.LayoutParams(dp(32f), dp(32f), Gravity.CENTER))
        }
        rb.addView(actionItemWithLabelCustom(favoriteContainer, favoriteLabel))
        var commentCount = 0
        val commentLabel = makeTv("", 11f, C_WHITE85).apply { gravity = Gravity.CENTER; text = formatCount(0) }
        val commentIv = ImageView(c).apply {
            setImageResource(com.qingmo.app.R.drawable.review)
            adjustViewBounds = true
        }
        val commentContainer = FrameLayout(c).apply {
            setPadding(dp(0f), dp(0f), dp(0f), dp(0f))
            val lp = FrameLayout.LayoutParams(dp(64f), dp(64f), Gravity.CENTER)
            commentIv.layoutParams = lp
            addView(commentIv, FrameLayout.LayoutParams(dp(32f), dp(32f), Gravity.CENTER))
        }
        rb.addView(actionItemWithLabelCustom(commentContainer, commentLabel))
        var shareCount = 0
        val shareLabel = makeTv("", 11f, C_WHITE85).apply { gravity = Gravity.CENTER; text = formatCount(0) }
        val shareIv = TextView(c).apply {
            text = "\u2197"
            textSize = 24f
            setTextColor(C_WHITE85)
            gravity = Gravity.CENTER
        }
        val shareContainer = FrameLayout(c).apply {
            setPadding(dp(0f), dp(0f), dp(0f), dp(0f))
            val lp = FrameLayout.LayoutParams(dp(64f), dp(64f), Gravity.CENTER)
            shareIv.layoutParams = lp
            addView(shareIv, FrameLayout.LayoutParams(dp(32f), dp(32f), Gravity.CENTER))
            setOnClickListener {
                shareCount +=1
                shareLabel.text = formatCount(shareCount)
            }
        }
        val likeLabel = makeTv("", 11f, C_WHITE85).apply { gravity = Gravity.CENTER; text = formatCount(0) }
        val likeIv = ImageView(c).apply {
            setImageResource(com.qingmo.app.R.drawable.dislike)
            adjustViewBounds = true
        }
        val likeContainer = FrameLayout(c).apply {
            setPadding(dp(0f), dp(0f), dp(0f), dp(0f))
            addView(likeIv, FrameLayout.LayoutParams(dp(32f), dp(32f), Gravity.CENTER))
        }
        rb.addView(actionItemWithLabelCustom(likeContainer, likeLabel))
        rb.addView(actionItemWithLabelCustom(shareContainer, shareLabel))
        val bi =
            LinearLayout(c).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16f), 0, dp(16f), 0)
                layoutParams =
                    FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { setMargins(0, 0, 0, dp(62f)) }
            }
        r.addView(bi)
        val danmakuBtn =
            ImageView(c).apply {
                setImageResource(com.qingmo.app.R.drawable.danmu)
                adjustViewBounds = true
                setPadding(dp(4f), dp(4f), dp(4f), dp(4f))
                background = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(0x40FFFFFF),
                    null,
                    null
                )
                setOnClickListener {
                    onDanmakuClick?.invoke()
                }
            }
        bi.addView(danmakuBtn, LinearLayout.LayoutParams(dp(36f), dp(36f)))
        bi.addView(Space(c).apply { minimumHeight = dp(6f) })
        bi.addView(
            makeTv(detail.title, 18f, C_WHITE, true).apply {
                maxLines = 1
                ellipsize =
                    android.text.TextUtils.TruncateAt.END
                setOnClickListener { onGoDetail() }
            },
        )
        bi.addView(Space(c).apply { minimumHeight = dp(2f) })
        val descTv =
            makeTv(detail.description ?: "", 13f, C_WHITE70).apply {
                maxLines = 1
                ellipsize =
                    android.text.TextUtils.TruncateAt.END
                setOnClickListener {
                    if (maxLines ==
                        1
                    ) {
                        maxLines = 99
                        ellipsize = null
                    } else {
                        maxLines = 1
                        ellipsize =
                            android.text.TextUtils.TruncateAt.END
                    }
                }
            }
        bi.addView(descTv)
        val timeLabel =
            makeTv("", 12f, C_WHITE85).apply {
                visibility = View.GONE
                gravity = Gravity.CENTER
                layoutParams =
                    FrameLayout
                        .LayoutParams(
                            -2,
                            -2,
                            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
                        ).apply { setMargins(0, 0, 0, dp(78f)) }
            }
        r.addView(timeLabel)
        val peekLabel =
            makeTv("", 13f, 0xFFFFFFFF.toInt(), true).apply {
                visibility = View.GONE
                gravity = Gravity.CENTER
                setBackgroundColor(0xCC1A535C.toInt())
                setPadding(dp(12f), dp(8f), dp(12f), dp(8f))
                layoutParams =
                    FrameLayout
                        .LayoutParams(
                            -2,
                            -2,
                            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
                        ).apply { setMargins(0, 0, 0, dp(100f)) }
            }
        r.addView(peekLabel)
        val seekBar = SeekBar(c, timeLabel, peekLabel)
        seekBar.layoutParams =
            FrameLayout.LayoutParams(-1, dp(24f), Gravity.BOTTOM).apply { setMargins(dp(20f), 0, dp(20f), dp(40f)) }
        r.addView(seekBar)
        val bottomBar =
            LinearLayout(c).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16f), dp(6f), dp(16f), dp(6f))
                layoutParams =
                    FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM)
            }
        r.addView(bottomBar)
        val epCard =
            LinearLayout(c).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16f), dp(6f), dp(16f), dp(6f))
                background =
                    GradientDrawable().apply {
                        setColor(C_DARK_BG)
                        cornerRadius = dp(12f).toFloat()
                    }
                setOnClickListener { onShowEps() }
            }
        bottomBar.addView(epCard, LinearLayout.LayoutParams(0, -2, 1f))
        epCard.addView(
            makeTv("\u9009\u96C6 \u00B7 \u5DF2\u5B8C\u7ED3 \u00B7 \u5168${eps.size}\u96C6", 14f, C_WHITE, true),
            LinearLayout.LayoutParams(0, -2, 1f),
        )
        epCard.addView(makeTv("\u25B2", 14f, C_WHITE70), LinearLayout.LayoutParams(-2, -2))
        bottomBar.addView(Space(c).apply { layoutParams = LinearLayout.LayoutParams(dp(12f), 0) })
        val fsBtn =
            ImageView(c).apply {
                setImageResource(com.qingmo.app.R.drawable.fullscreen_enter)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setOnClickListener {
                    isFullscreen = !isFullscreen
                    onFullscreenChange?.invoke(isFullscreen)
                    setFullscreen(isFullscreen)
                    setImageResource(
                        if (isFullscreen) {
                            com.qingmo.app.R.drawable.fullscreen_exit
                        } else {
                            com.qingmo.app.R.drawable.fullscreen_enter
                        },
                    )
                }
                layoutParams = LinearLayout.LayoutParams(dp(28f), dp(28f))
            }
        bottomBar.addView(fsBtn)
        return VH(r, pv, danmakuView, titleTv, speedTv, descTv, danmakuBtn, seekBar, timeLabel, peekLabel, tb, rb, bi, likeLabel, commentLabel, favoriteLabel, likeIv, favoriteIv = favoriteIv)
    }

    override fun onBindViewHolder(
        h: VH,
        pos: Int,
    ) {
        val ep = eps[pos]
        viewHolders.put(pos, h)
        // 核心修复：软键盘弹出ViewHolder彻底重建 → 立刻从本地缓存恢复弹幕，不需要重新加载网络
        danmakuCache[ep.episodeId]?.let { cachedItems ->
            h.danmakuView.setDanmakuData(cachedItems)
            danmakuDataSetTriggered.add(ep.episodeId)
            players[pos]?.currentPosition?.let { posMs ->
                h.danmakuView.updatePlaybackTime(posMs)
            }
        }
        if (pos == cur) activeVh = h
        h.titleTv.text = "\u7B2C${ep.episodeNum}\u96C6"
        h.speedTv.text = "${rate}x"
        // 加载计数 + 点赞状态（绑定用户 ID）
        h.countsLoaded = false
        scope.launch(Dispatchers.IO) {
            try {
                val resp = RetrofitClient.api.getEpisodeCounts(ep.episodeId, userId)
                val likeCnt = (resp["like_count"] as? Number)?.toInt() ?: 0
                val commentCnt = (resp["comment_count"] as? Number)?.toInt() ?: 0
                val liked = resp["liked"] as? Boolean ?: false
                h.likeLabel.post {
                    h.isLiked = liked
                    h.likeCount = likeCnt
                    h.likeLabel.text = formatCount(likeCnt)
                    h.likeIv.setImageResource(if (liked) com.qingmo.app.R.drawable.like else com.qingmo.app.R.drawable.dislike)
                    h.countsLoaded = true
                }
                h.commentLabel.post { h.commentLabel.text = formatCount(commentCnt) }
            } catch (_: Exception) {}
        }
        // 加载弹幕：完全绕过Compose状态竞态
        h.danmakuView.setDanmakuEnabled(danmakuGlobalEnabled)
        h.danmakuView.setCurrentUserId(userId)
        if (danmakuGlobalEnabled && danmakuLoaded.add(ep.episodeId)) {
            scope.launch(Dispatchers.IO) {
                try {
                    val items = RetrofitClient.api.getDanmaku(ep.episodeId).map { r ->
                        val colorStr = (r["color"] as? String) ?: "#ffffff"
                        val color = try { AndroidColor.parseColor(colorStr) } catch (_: Exception) { AndroidColor.WHITE }
                        DanmakuItem(
                            id = ((r["id"] as? Number)?.toLong() ?: 0L),
                            text = (r["text"] as? String) ?: "",
                            timeSec = ((r["time_sec"] as? Number)?.toFloat() ?: 0f),
                            color = color,
                            userId = (r["user_id"] as? String) ?: "",
                        )
                    }
                    danmakuCache[ep.episodeId] = items
                    h.danmakuView.post { h.danmakuView.setDanmakuData(items) }
                } catch (_: Exception) {}
            }
        } else if (danmakuGlobalEnabled) {
            danmakuCache[ep.episodeId]?.let { items ->
                h.danmakuView.post { h.danmakuView.setDanmakuData(items) }
            }
        }
        // 加载该集高光点 → 进度条画青色标记点
        scope.launch(Dispatchers.IO) {
            try {
                val playbackWrap = RetrofitClient.api.getPlaybackInfo(ep.episodeId)
                val highlightList = playbackWrap.highlights
                h.seekBar.post { h.seekBar.setHighlights(highlightList) }
                highlightCache[ep.episodeId.toInt()] = highlightList
            } catch (_: Exception) {}
        }
        // 点击 100%真实addView顺序：0=收藏 1=评论 2=点赞 3=分享
        val rg = h.rightBar as ViewGroup
        // 收藏按钮（收藏状态加载完成后才响应，先乐观更新 UI 再发 API）
        h.favoritesLoaded = false
        scope.launch(Dispatchers.IO) {
            try {
                val favList = RetrofitClient.api.getFavorites(userId)
                val favIds = favList.mapNotNull { (it["drama_id"] as? Number)?.toInt() }
                val isFav = detail.id.toInt() in favIds
                h.favoriteIv.post {
                    h.isFavorited = isFav
                    h.favoriteIv.setImageResource(if (isFav) com.qingmo.app.R.drawable.starred else com.qingmo.app.R.drawable.unstarred)
                    h.favoriteLabel.text = formatCount(favList.size)
                    h.favoritesLoaded = true
                }
            } catch (_: Exception) {}
        }
        rg.getChildAt(0).setOnClickListener {
            if (!h.favoritesLoaded) return@setOnClickListener
            h.isFavorited = !h.isFavorited
            // 乐观更新收藏数
            val cur = h.favoriteLabel.text.toString().toIntOrNull() ?: 0
            h.favoriteLabel.text = formatCount(if (h.isFavorited) cur + 1 else cur - 1)
            h.favoriteIv.setImageResource(if (h.isFavorited) com.qingmo.app.R.drawable.starred else com.qingmo.app.R.drawable.unstarred)
            h.favoriteIv.animate().scaleX(1.15f).scaleY(1.15f).setDuration(80).withEndAction {
                h.favoriteIv.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }.start()
            onFavoriteClick?.invoke(detail.id.toLong())
        }
        // 评论按钮 就是review.png图标 绝对正确位置
        rg.getChildAt(1).setOnClickListener {
            // 先弹跳动效
            (rg.getChildAt(1) as ViewGroup).getChildAt(0).animate().scaleX(1.15f).scaleY(1.15f).setDuration(80).withEndAction {
                (rg.getChildAt(1) as ViewGroup).getChildAt(0).animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }.start()
            onCommentClick?.invoke(ep.episodeId)
        }
        // 点赞按钮（counts 加载完成后才响应，先乐观更新 UI 再发 API）
        rg.getChildAt(2).setOnClickListener {
            if (!h.countsLoaded) return@setOnClickListener
            h.isLiked = !h.isLiked
            h.likeCount = if (h.isLiked) h.likeCount + 1 else h.likeCount - 1
            h.likeLabel.text = formatCount(h.likeCount)
            h.likeIv.setImageResource(if (h.isLiked) com.qingmo.app.R.drawable.like else com.qingmo.app.R.drawable.dislike)
            h.likeIv.animate().scaleX(1.15f).scaleY(1.15f).setDuration(80).withEndAction {
                h.likeIv.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }.start()
            onLikeClick?.invoke(ep.episodeId)
        }
        // 分享按钮
        rg.getChildAt(3).setOnClickListener {  }
        val player =
            players[pos] ?: run {
                val sp = ProgressCache.get(ep.episodeId)
                // 直接用episode_num作为真实MP4文件名，北派#1本身就是63~81集，零偏移零出错
                val localVideoUrl = "videos/${detail.id}/${ep.episodeNum}.mp4"
                ExoPlayer.Builder(ctx)
                    .setLoadControl(
                        DefaultLoadControl.Builder()
                            .setBufferDurationsMs(
                                50_000,      // 最小开始播放缓冲 50ms 秒开
                                200_000,     // 最大预缓冲 200ms
                                2500,        // 回放缓冲 2.5s
                                5000         // 重新加载缓冲阈值 5s
                            )
                            .setPrioritizeTimeOverSizeThresholds(true)
                            .build()
                    )
                    .build().apply {
                        setPlaybackSpeed(rate)
                        addListener(
                            object : Player.Listener {
                                override fun onPlaybackStateChanged(state: Int) {
                                    if (state ==
                                        Player.STATE_ENDED &&
                                        pos == cur
                                    ) {
                                        ProgressCache.markWatched(eps[pos].episodeId)
                                        ProgressCache.save(eps[pos].episodeId, 0L)
                                        // 集结束陪看播报
                                        val hlTitles = highlightCache[ep.episodeId.toInt()]
                                            ?.take(3)?.joinToString("、") { it.title } ?: ""
                                        val endMsg = if (hlTitles.isNotEmpty()) "📺 本集高能：$hlTitles" else "📺 本集结束~"
                                        sendXiaomoDanmaku(ep.episodeId, endMsg, delayMs = 500)
                                        // 连看成就
                                        val nextPos = pos + 1
                                        if (nextPos < eps.size && detail.id.toLong() == lastWatchedDramaId) {
                                            consecutiveCount++
                                        } else {
                                            consecutiveCount = 1
                                        }
                                        lastWatchedDramaId = detail.id.toLong()
                                        val milestones = setOf(3, 5, 10, 20, 30)
                                        if (consecutiveCount in milestones) {
                                            scope.launch {
                                                kotlinx.coroutines.delay(1500)
                                                sendXiaomoDanmaku(ep.episodeId, "🔥 连看${consecutiveCount}集！你是真上头了！")
                                            }
                                        }
                                        onEpisodeEnd(nextPos)
                                    }
                                }
                                override fun onIsPlayingChanged(isPlaying: Boolean) {
                                    if (isPlaying) h.danmakuView.resumeDanmaku()
                                    else h.danmakuView.pauseDanmaku()
                                }
                            },
                        )
                        // 立即设置媒体源+开始预加载，不等任何网络IO
                        setMediaItem(MediaItem.fromUri(RetrofitClient.resolveMediaUrl(localVideoUrl)))
                        prepare()
                        if (sp > 0L) {
                            seekTo(sp)
                        }
                        players[pos] = this
                    }
            }
        h.pv.player = player
        progressJobs[pos]?.cancel()
        progressJobs[pos] =
            scope.launch {
                var last = 0L
                val epHighlights = highlightCache[ep.episodeId.toInt()] ?: emptyList()
                while (true) {
                    val p = player.currentPosition
                    if (player.isPlaying) {
                        if (p - last >= 2000) {
                            ProgressCache.save(ep.episodeId, p)
                            last = p
                        }
                        // 高光点自动检测：播放进度到达高光点 ±3s 内自动触发
                        for (hl in epHighlights) {
                            val hlMs = (hl.time * 1000).toLong()
                            if (kotlin.math.abs(p - hlMs) < 3000 && triggeredSet.add(hl.id)) {
                                com.qingmo.app.xiaomo.XiaoMoCore.triggerDanmakuHint(hl)
                                // AI 替身自动发弹幕
                                val autoText = hl.emotionHints?.randomOrNull() ?: hl.title
                                sendXiaomoDanmaku(ep.episodeId, autoText)
                                break
                            }
                        }
                    }
                    h.danmakuView.updatePlaybackTime(p)
                    delay(200)
                }
            }
        val savedMs = ProgressCache.get(ep.episodeId)
        // 集开始陪看播报：从头开始看时自动发小墨弹幕
        if (savedMs <= 1000L) {
            val startMsg = "🎬 第${ep.episodeNum}集来啦~"
            sendXiaomoDanmaku(ep.episodeId, startMsg, delayMs = 1200)
        }
        val dur = ep.duration * 1000L
        h.seekBar.setProgress(if (dur > 0 && savedMs > 0) savedMs.toFloat() / dur else 0f, dur)
        h.seekBar.onSeek = { ms -> player.seekTo(ms); h.danmakuView.seekTo(ms) }
        h.seekBar.setPlayer(player)
        h.seekBar.onDragChange =
            { dragging ->
                val vis =
                    if (dragging ||
                        isFullscreen
                    ) {
                        View.INVISIBLE
                    } else {
                        View.VISIBLE
                    }
                ; h.topBar.visibility = vis
                h.rightBar.visibility = vis
                h.bottomInfo.visibility =
                    vis
                viewPager2?.requestDisallowInterceptTouchEvent(dragging)
            }
        applyFullscreenToVh(h)
    }

    private fun applyFullscreenToVh(vh: VH) {
        val vis = if (isFullscreen) View.INVISIBLE else View.VISIBLE
        vh.topBar.visibility = vis
        vh.rightBar.visibility = vis
        vh.bottomInfo.visibility = vis
        val bb = vh.root.getChildAt(vh.root.childCount - 1) as? LinearLayout
        val fb = bb?.getChildAt(2) as? ImageView
        fb?.setImageResource(
            if (isFullscreen) com.qingmo.app.R.drawable.fullscreen_exit
            else com.qingmo.app.R.drawable.fullscreen_enter,
        )
    }

    override fun onViewRecycled(holder: VH) {
        super.onViewRecycled(holder)
        val pos = holder.absoluteAdapterPosition
        holder.danmakuView.clearDanmaku()
        if (pos != RecyclerView.NO_POSITION) {
            viewHolders.remove(pos)
            progressJobs[pos]?.cancel()
            progressJobs.remove(pos)
            // Release ExoPlayer if far from current position (keep cur-1..cur+1)
            if (abs(pos - cur) > 2) {
                players[pos]?.release()
                players.remove(pos)
            }
        }
    }

    private fun updateDanmaku(btn: TextView) {
        btn.setTextColor(if (danmaku) C_WHITE85 else C_WHITE50)
        btn.background =
            GradientDrawable().apply {
                cornerRadius = dp(6f).toFloat()
                setColor(if (danmaku) 0x38FFFFFF else C_WHITE20)
            }
        // 更新所有 ViewHolder 的弹幕显示状态
        for (i in 0 until viewHolders.size()) {
            viewHolders.valueAt(i)?.danmakuView?.setDanmakuEnabled(danmaku)
        }
    }

    fun addDanmakuToCurrent(episodeId: Long, item: DanmakuItem, forceEmit: Boolean = false) {
        // 更新缓存
        val list = danmakuCache[episodeId]?.toMutableList() ?: mutableListOf()
        list.add(item)
        danmakuCache[episodeId] = list
        // 添加到所有展示该剧集 ViewHolder 的排期队列
        for (i in 0 until viewHolders.size()) {
            val h = viewHolders.valueAt(i)
            val pos = viewHolders.keyAt(i)
            if (h != null && pos < eps.size && eps[pos].episodeId == episodeId) {
                if (forceEmit) h.danmakuView.forceEmitDanmaku(item)
                else h.danmakuView.addPendingDanmaku(item)
            }
        }
    }

    // ===== 小墨互动增强 =====
    private val xiaomoPurple = 0xFFC864FF.toInt()

    private fun sendXiaomoDanmaku(episodeId: Long, text: String, delayMs: Long = 0) {
        val nowMs = activePlayer?.currentPosition ?: 0L
        val item = DanmakuItem(
            id = System.currentTimeMillis(),
            text = text,
            timeSec = nowMs / 1000f,
            color = xiaomoPurple,
            userId = "xiaomo_agent",
        )
        if (delayMs > 0) {
            scope.launch {
                kotlinx.coroutines.delay(delayMs)
                addDanmakuToCurrent(episodeId, item, forceEmit = true)
            }
        } else {
            addDanmakuToCurrent(episodeId, item, forceEmit = true)
        }
    }

    /** 弹幕接龙：用户发弹幕后小墨有概率接话 */
    fun onUserDanmaku(episodeId: Long, userText: String) {
        if (kotlin.random.Random.nextInt(100) >= 20) return // 20% 概率
        val replyPool = when {
            userText.contains("哈哈") || userText.contains("笑") || userText.contains("😂") ->
                listOf("我也笑喷了😂", "太搞笑了", "哈哈哈哈", "笑死我了")
            userText.contains("甜") || userText.contains("❤") || userText.contains("嗑") ->
                listOf("磕到了🥰", "太甜了吧！", "我也嗑这对！")
            userText.contains("啊") || userText.contains("天哪") || userText.contains("😱") ->
                listOf("没想到啊！", "我人傻了", "真的假的！", "震惊！")
            userText.contains("帅") || userText.contains("美") || userText.contains("好看") ->
                listOf("确实好看！", "颜值暴击😍", "完全认同")
            else ->
                listOf("确实！", "说得好！", "嗯嗯~", "+1", "同感！")
        }
        val reply = replyPool.random()
        val delayMs = (2000..4000L).random()
        sendXiaomoDanmaku(episodeId, reply, delayMs)
    }

    fun setActive(pos: Int) {
        activePlayer?.playWhenReady = false
        cur = pos
        activePlayer = players[pos]
        activePlayer?.playWhenReady =
            true
        activePlayer?.setPlaybackSpeed(rate)
        val sp = ProgressCache.get(eps[pos].episodeId)
        activePlayer?.seekTo(sp)
        // Find ViewHolder for new position (may have been prefetched before onBindViewHolder set activeVh)
        val vh = viewHolders.get(pos)
        if (vh != null) activeVh = vh
        applyFullscreenToActiveVh()
    }

    fun setRate(r: Float) {
        rate = r
        activePlayer?.setPlaybackSpeed(r)
        activeVh?.speedTv?.text = "${rate}x"
    }

    fun setFullscreen(fs: Boolean) {
        isFullscreen = fs
        applyFullscreenToActiveVh()
    }

    private fun applyFullscreenToActiveVh() {
        activeVh?.let { applyFullscreenToVh(it) }
    }

    fun updateProgressBar(
        progress: Float,
        duration: Long,
    ) {
        activeVh?.seekBar?.setProgress(progress, duration)
    }

    fun preparePlayer(pos: Int) {
        if (players.containsKey(pos)) return
        val ep = eps[pos]
        val sp = ProgressCache.get(ep.episodeId)
        ExoPlayer.Builder(ctx).build().apply {
            setPlaybackSpeed(rate)
            playWhenReady =
                false
            addListener(
                object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state ==
                            Player.STATE_ENDED &&
                            pos == cur
                        ) {
                            ProgressCache.markWatched(eps[pos].episodeId)
                            ProgressCache.save(eps[pos].episodeId, 0L)
                            onEpisodeEnd(
                                pos + 1,
                            )
                        }
                    }
                },
            )
            scope.launch {
                repo.getPlaybackInfo(ep.episodeId).onSuccess { info ->
                    setMediaItem(MediaItem.fromUri(RetrofitClient.resolveMediaUrl(info.videoUrl)))
                    prepare()
                    if (sp >
                        0L
                    ) {
                        seekTo(sp)
                    }
                }
            }
            players[pos] = this
        }
    }

    fun releaseAll() {
        progressJobs.values.forEach { it.cancel() }
        progressJobs.clear()
        players.values.forEach { it.release() }
        players.clear()
    }

    inner class SeekBar(
        context: Context,
        private val timeLabel: TextView,
        private val peekLabel: TextView,
    ) : View(context) {
        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x20FFFFFF.toInt() }
        private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x60FFFFFF.toInt() }
        private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xB3FFFFFF.toInt() }
        private val highlightDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4ECDC4.toInt() }
        private var progress = 0f
        private var totalDuration = 0L
        private var isDragging = false
        private val thumbRadius = dp(4f).toFloat()
        private val trackHeight = dp(2f).toFloat()
        var onSeek: ((Long) -> Unit)? = null
        var onDragChange: ((Boolean) -> Unit)? = null
        private var seekPlayer: ExoPlayer? = null
        private var highlights: List<com.qingmo.app.data.model.HighlightItem> = emptyList()
        private var longPressJob: kotlinx.coroutines.Job? = null

        fun setPlayer(p: ExoPlayer?) {
            seekPlayer = p
        }

        fun setHighlights(list: List<com.qingmo.app.data.model.HighlightItem>) {
            highlights = list
            postInvalidate()
        }

        fun setProgress(
            p: Float,
            dur: Long,
        ) {
            if (!isDragging) {
                progress = p.coerceIn(0f, 1f)
                totalDuration = dur
                postInvalidate()
            }
        }

        private val safePadding = dp(7f).toFloat() // 左右安全边距 完全避免圆点裁切
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cy = height / 2f
            val w = (width - 2*safePadding).toFloat()
            val th = if (isDragging) dp(4f).toFloat() else trackHeight
            val tr = if (isDragging) 0x40FFFFFF.toInt() else trackPaint.color
            val pr = if (isDragging) 0xE0FFFFFF.toInt() else progressPaint.color
            canvas.drawRoundRect(
                safePadding,
                cy - th / 2,
                width - safePadding,
                cy + th / 2,
                th / 2,
                th / 2,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tr },
            )
            val px = safePadding + w * progress
            if (px > safePadding) {
                canvas.drawRoundRect(
                    safePadding,
                    cy - th / 2,
                    px,
                    cy + th / 2,
                    th / 2,
                    th / 2,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pr },
                )
            }
            highlights.forEach { h ->
                val hProgress = if (totalDuration > 0) (h.time * 1000f / totalDuration).coerceIn(0f,1f) else 0f
                val dotX = safePadding + w * hProgress
                canvas.drawCircle(dotX, cy, dp(3.5f).toFloat(), highlightDotPaint)
            }
            canvas.drawCircle(px, cy, if (isDragging) dp(6f).toFloat() else thumbRadius, thumbPaint)
        }

        override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
            val effectiveW = (width - 2*safePadding)
            val touchXInTrack = (event.x - safePadding).coerceIn(0f, effectiveW.toFloat())
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    isDragging = true
                    updateProgress(touchXInTrack, effectiveW.toFloat())
                    timeLabel.visibility = View.VISIBLE
                    updateTimeLabel()
                    peekLabel.visibility = View.GONE
                    val downProgress = (touchXInTrack / effectiveW).coerceIn(0f, 1f)
                    val curSec = if (totalDuration > 0) (downProgress * totalDuration / 1000f) else 0f
                    val nearHighlight = highlights.minByOrNull { abs(it.time - curSec) }
                    if (nearHighlight != null && abs(nearHighlight.time - curSec) < 8f) {
                        longPressJob?.cancel()
                        longPressJob = scope.launch {
                            kotlinx.coroutines.delay(300L)
                            peekLabel.text = nearHighlight.title
                            peekLabel.visibility = View.VISIBLE
                        }
                    }
                    onDragChange?.invoke(true)
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    longPressJob?.cancel()
                    updateProgress(touchXInTrack, effectiveW.toFloat())
                    updateTimeLabel()
                    // 拖拽移动过程中实时检测附近8秒范围内的高光点，手指滑到高光点立刻显示提示
                    val moveProgress = (touchXInTrack / effectiveW).coerceIn(0f, 1f)
                    val curSec = if (totalDuration > 0) (moveProgress * totalDuration / 1000f) else 0f
                    val nearHighlight = highlights.minByOrNull { abs(it.time - curSec) }
                    if (nearHighlight != null && abs(nearHighlight.time - curSec) < 8f) {
                        peekLabel.text = nearHighlight.title
                        peekLabel.visibility = View.VISIBLE
                    } else {
                        peekLabel.visibility = View.GONE
                    }
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    longPressJob?.cancel()
                    peekLabel.visibility = View.GONE
                    isDragging = false
                    timeLabel.visibility = View.GONE
                    onDragChange?.invoke(false)
                    if (progress > 0.98f && totalDuration > 0) {
                        ProgressCache.markWatched(eps[cur].episodeId)
                        ProgressCache.save(eps[cur].episodeId, 0L)
                        onEpisodeEnd(cur + 1)
                    } else {
                        val p = seekPlayer
                        val rawPos = (progress * totalDuration).toLong()
                        val pos =
                            if (totalDuration > 2000) {
                                rawPos.coerceAtMost(totalDuration - 500)
                            } else {
                                rawPos
                            }
                        p?.seekTo(pos)
                        onSeek?.invoke(pos)
                    }
                }
            }
            return true
        }

        private fun updateProgress(xInTrack: Float, effectiveW: Float) {
            progress = (xInTrack / effectiveW).coerceIn(0f, 1f)
            postInvalidate()
        }

        private fun updateTimeLabel() {
            val cur = (progress * totalDuration).toLong()
            timeLabel.text =
                "${fmt(cur)} / ${fmt(totalDuration)}"
        }

        private fun fmt(ms: Long): String {
            val s = ms / 1000
            return "%02d:%02d".format(s / 60, s % 60)
        }
    }

    private fun makeTv(
        text: String,
        size: Float,
        color: Int,
        bold: Boolean = false,
    ): TextView =
        TextView(ctx).apply {
            this.text =
                text
            ; setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, size)
            setTextColor(color)
            if (bold) {
                typeface =
                    android.graphics.Typeface.DEFAULT_BOLD
            }
        }

    private fun actionItem(
        icon: String,
        label: String,
    ): LinearLayout {
        val item =
            LinearLayout(ctx).apply {
                orientation =
                    LinearLayout.VERTICAL
                ; gravity = Gravity.CENTER
                setPadding(0, dp(4f), 0, dp(24f))
            }
        ; item.addView(
            makeTv(icon, 24f, C_WHITE85).apply {
                gravity =
                    Gravity.CENTER
            },
        )
        item.addView(makeTv(label, 11f, C_WHITE85).apply { gravity = Gravity.CENTER })
        return item
    }

    private fun actionItemWithLabel(icon: String, labelTv: TextView): LinearLayout {
        val item = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(0, dp(0f), 0, dp(0f)) }
        item.addView(makeTv(icon, 24f, C_WHITE85).apply { gravity = Gravity.CENTER })
        item.addView(labelTv)
        return item
    }

    private fun actionItemWithLabelCustom(iconView: android.view.View, labelTv: TextView): LinearLayout {
        val item = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(0, dp(0f), 0, dp(0f)) }
        val lp = FrameLayout.LayoutParams(dp(64f), dp(64f), Gravity.CENTER)
        iconView.layoutParams = lp
        item.addView(iconView)
        item.addView(labelTv)
        return item
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun ESheet(
    eps: List<EpisodeBrief>,
    curEpId: Long,
    total: Int,
    onSelect: (EpisodeBrief) -> Unit,
    onDismiss: () -> Unit,
) {
    // Bottom sheet overlay
    Box(Modifier.fillMaxSize()) {
        // Dimmed backdrop
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { onDismiss() })
        // Bottom sheet panel — 青白 theme
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.6f),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = Background,
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "\u9009\u96C6\uFF08\u5171${total}\u96C6\uFF09",
                        color = OnBackground,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "\u2192 \u8FD4\u56DE\u64AD\u653E",
                        color = Primary,
                        fontSize = 14.sp,
                        modifier =
                            Modifier.clickable {
                                onDismiss()
                            },
                    )
                }
                Spacer(Modifier.height(16.dp))
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    eps.chunked(5).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { ep ->
                                val cur = ep.episodeId == curEpId
                                val watched = ProgressCache.isWatched(ep.episodeId)
                                val bgColor =
                                    when {
                                        cur -> Primary.copy(alpha = 0.12f)
                                        watched -> SurfaceVariant.copy(alpha = 0.5f)
                                        else -> Color.White
                                    }
                                val textColor =
                                    when {
                                        cur -> Primary
                                        watched -> OnSurfaceVariant
                                        else -> OnSurface
                                    }
                                val border =
                                    if (cur) {
                                        BorderStroke(
                                            1.dp,
                                            Primary,
                                        )
                                    } else {
                                        BorderStroke(0.5.dp, Border)
                                    }
                                Surface(
                                    Modifier.weight(1f).aspectRatio(1f).clickable {
                                        onSelect(ep)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = bgColor,
                                    border = border,
                                ) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(
                                            "${ep.episodeNum}",
                                            color = textColor,
                                            fontSize = 14.sp,
                                            fontWeight = if (cur) FontWeight.Bold else FontWeight.Normal,
                                        )
                                    }
                                }
                            }
                            repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun SS(
    current: Float,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    Surface(
        onClick = onDismiss,
        modifier = Modifier.fillMaxSize(),
        color = Color.Black.copy(alpha = 0.55f),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Background,
                tonalElevation = 4.dp,
                shadowElevation = 8.dp,
            ) {
                Column(
                    Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "\u500D\u901F\u64AD\u653E",
                        color = OnBackground,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(16.dp))
                    speeds.forEach { sp ->
                        val selected = abs(sp - current) < 0.01f
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) Primary else Color.Transparent,
                            border = if (selected) null else BorderStroke(1.dp, Border),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(sp) },
                        ) {
                            Box(
                                Modifier.padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "${sp}x",
                                    color = if (selected) Color.White else OnSurface,
                                    fontSize = 16.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                )
                            }
                        }
                        if (sp != speeds.last()) {
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onDismiss) {
                        Text("\u53D6\u6D88", color = OnSurfaceVariant, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun Load() =
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Text("\u52A0\u8F7D\u4E2D\u2026", color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp)
    }

@Suppress("ktlint:standard:function-naming")
@Composable
private fun Err(
    msg: String,
    onRetry: (() -> Unit)? = null,
) =
    Column(
        Modifier.fillMaxSize().background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(msg, color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)
        if (onRetry != null) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onRetry) {
                Text("\u91CD\u8BD5", color = PlayerAccent, fontSize = 15.sp)
            }
        }
    }

@Suppress("ktlint:standard:function-naming")
@Composable
private fun CommentItem(
    id: Int, nickname: String, text: String,
    replyToUserName: String?,
    showTime: String, likeCount: Int, liked: Boolean,
    replies: List<Map<String, Any>>, isExpanded: Boolean,
    isMine: Boolean,
    isXiaomo: Boolean = false,
    repliesMine: List<Boolean>,
    likeCounts: MutableMap<Int, Int>, likedSet: MutableSet<Int>,
    expandMap: MutableMap<Int, Boolean>,
    onReply: (targetCommentId: Int, targetNickname: String, isTopLevelTarget: Boolean) -> Unit,
    onLike: (Int, Boolean) -> Unit,
    onToggleExpand: (Int) -> Unit,
) {
    val avatarSize = 40.dp
    val avatarFont = 16.sp
    val nameFont = 15.sp
    val contentFont = 14.sp

    // 顶级评论无缩进，所有子回复统一全局16dp缩进，永远不叠加
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Surface(shape = CircleShape, color = if (isXiaomo) Color(0xFF7C4DFF) else Color(0xFFE8F6F7), modifier = Modifier.size(avatarSize)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(nickname.first().uppercase(), color = Color.White, fontSize = avatarFont, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(nickname, fontSize = nameFont, fontWeight = FontWeight.Bold, color = if (isXiaomo) Color(0xFF7C4DFF) else Color(0xFF333333))
                    if (isXiaomo) {
                        Spacer(Modifier.width(6.dp))
                        Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF7C4DFF).copy(alpha=0.12f)) {
                            Text("小墨 AI", fontSize = 11.sp, color = Color(0xFF7C4DFF), fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal=6.dp, vertical=1.dp))
                        }
                    } else if (isMine) {
                        Spacer(Modifier.width(6.dp))
                        Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF1E88E5).copy(alpha=0.12f)) {
                            Text("我的", fontSize = 11.sp, color = Color(0xFF1A535C), fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal=6.dp, vertical=1.dp))
                        }
                    }
                }
                Text(text, fontSize = contentFont, color = Color(0xFF444444), lineHeight = (contentFont.value * 1.4f).sp, modifier = Modifier.padding(top = 2.dp))
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row {
                        Text(showTime, fontSize = 12.sp, color = Color(0xFF999999))
                        if (!isXiaomo) {
                            Spacer(Modifier.width(12.dp))
                            Text("回复", fontSize = 12.sp, color = Color(0xFF1A535C), modifier = Modifier.clickable { onReply(id, nickname, true) })
                        }
                    }
                    Row(Modifier.clickable { onLike(id, liked) }) {
                        Text(if (liked) "❤️" else "🤍", fontSize = 14.sp)
                        if (likeCount > 0) Text(" $likeCount", fontSize = 12.sp, color = if (liked) Color(0xFFE53935) else Color(0xFF999999))
                    }
                }
            }
        }

        val btnText = if (isExpanded) "收起回复" else "展开${replies.size}条回复"

        if (!isExpanded && replies.isNotEmpty()) {
            Text(btnText, fontSize = 12.sp, color = Color(0xFF1A535C), modifier = Modifier.padding(top = 4.dp, start = (avatarSize + 10.dp)).clickable { onToggleExpand(id) })
        }

        // 所有子回复统一缩进（对齐父评论文本区），完全平级，无递归叠加
        if (isExpanded && replies.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(start = (avatarSize + 10.dp))) {
                replies.forEachIndexed { idx, reply ->
                    ReplyItem(reply, idx, repliesMine, likeCounts, likedSet, onReply, onLike)
                }
                // 收起回复按钮放在所有子回复的最后面，仅渲染一次
                Text(btnText, fontSize = 12.sp, color = Color(0xFF1A535C), modifier = Modifier.padding(top = 6.dp).clickable { onToggleExpand(id) })
            }
        }
    }
}

@Composable
private fun ReplyItem(
    reply: Map<String, Any>,
    idx: Int,
    repliesMine: List<Boolean>,
    likeCounts: Map<Int, Int>,
    likedSet: Set<Int>,
    onReply: (Int, String, Boolean) -> Unit,
    onLike: (Int, Boolean) -> Unit,
) {
    val rId = (reply["id"] as? Number)?.toInt() ?: 0
    val rNick = reply["nickname"] as? String ?: "热心网友"
    val rText = reply["text"] as? String ?: ""
    val rReplyToUserName = reply["reply_to_nickname"] as? String ?: ""
    val rCreated = reply["created_at"] as? String ?: ""
    val rTime = if (rCreated.length >= 5) rCreated.substring(5, 10) else rCreated
    val rLike = likeCounts[rId] ?: 0
    val rLiked = rId in likedSet
    val rIsMine = if (idx < repliesMine.size) repliesMine[idx] else false
    val rSubReplies = (reply["replies"] as? List<*>)?.filterIsInstance<Map<String, Any>>() ?: emptyList()

    Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Surface(shape = CircleShape, color = Color(0xFFE3F2FD), modifier = Modifier.size(32.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(rNick.first().uppercase(), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(rNick, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF333333))
                    if (rIsMine) {
                        Spacer(Modifier.width(4.dp))
                        Surface(shape = RoundedCornerShape(3.dp), color = Color(0xFF1E88E5).copy(alpha = 0.12f)) {
                            Text("我的", fontSize = 10.sp, color = Color(0xFF1A535C), fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 4.dp, vertical = 0.5.dp))
                        }
                    }
                }
                val replyPrefix = if (rReplyToUserName.isNotEmpty()) "@$rReplyToUserName：" else ""
                if (replyPrefix.isNotEmpty()) {
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = Color(0xFF1A535C))) { append(replyPrefix) }
                            withStyle(SpanStyle(color = Color(0xFF444444))) { append(rText) }
                        },
                        fontSize = 13.sp, lineHeight = (13f * 1.4f).sp, modifier = Modifier.padding(top = 2.dp)
                    )
                } else {
                    Text(rText, fontSize = 13.sp, color = Color(0xFF444444), lineHeight = (13f * 1.4f).sp, modifier = Modifier.padding(top = 2.dp))
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row {
                        Text(rTime, fontSize = 11.sp, color = Color(0xFF999999))
                        Spacer(Modifier.width(12.dp))
                        Text("回复", fontSize = 11.sp, color = Color(0xFF1A535C), modifier = Modifier.clickable { onReply(rId, rNick, false) })
                    }
                    Row(Modifier.clickable { onLike(rId, rLiked) }) {
                        Text(if (rLiked) "❤️" else "🤍", fontSize = 12.sp)
                        if (rLike > 0) Text(" $rLike", fontSize = 11.sp, color = if (rLiked) Color(0xFFE53935) else Color(0xFF999999))
                    }
                }
            }
        }
        // 递归渲染该回复的子回复（同层级缩进，嵌套显示在下方）
        if (rSubReplies.isNotEmpty()) {
            Column(Modifier.fillMaxWidth()) {
                rSubReplies.forEachIndexed { subIdx, subReply ->
                    ReplyItem(subReply, subIdx, emptyList(), likeCounts, likedSet, onReply, onLike)
                }
            }
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
fun XiaoMoChatSheet(
    visible: Boolean,
    userId: String,
    dramaContext: Map<String, Any>?,
    onDismiss: () -> Unit,
) {
    // 全局记忆聊天消息，完全不绑定visible，退出抽屉永远不销毁聊天历史
    val persistentMessages = remember(userId) {
        mutableStateListOf<com.qingmo.app.data.chat.ChatMessage>().apply {
            // 首次打开自动追加欢迎消息
            add(
                com.qingmo.app.data.chat.ChatMessage(
                    id = 0,
                    role = com.qingmo.app.data.chat.ChatMessage.Role.XiaoMo,
                    content = "嗨！我是小墨，你的 AI 观剧伙伴~\n有什么想聊的吗？可以问我推荐短剧、讨论剧情，或者问我的看法哦！✨",
                ),
            )
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(250)) + fadeIn(tween(250)),
        exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(200)) + fadeOut(tween(200))
    ) {
        androidx.activity.compose.BackHandler { onDismiss() }
        Column(Modifier.fillMaxSize().imePadding()) {
            Box(Modifier.fillMaxWidth().weight(0.35f).clickable { onDismiss() })
            Surface(
                modifier = Modifier.fillMaxWidth().weight(0.65f),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                color = Color.White,
            ) {
                Column(Modifier.fillMaxSize()) {
                    var dragOffset by remember { mutableFloatStateOf(0f) }
                    val sheetHeightPx = with(LocalDensity.current) { androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp.toPx() * 0.65f }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 8.dp)
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragEnd = {
                                        if (dragOffset > sheetHeightPx * 0.3f) onDismiss()
                                        dragOffset = 0f
                                    },
                                    onDragCancel = { dragOffset = 0f },
                                    onVerticalDrag = { _, amount -> dragOffset += amount }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(Modifier.width(40.dp).height(4.dp).background(Color(0xFFDDDDDD), RoundedCornerShape(2.dp)))
                    }
                    Row(
                        Modifier.fillMaxWidth().height(48.dp).padding(start = 16.dp, end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("和小墨聊天", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF333333), modifier = Modifier.weight(1f))
                        Text("✕", fontSize = 20.sp, color = Color(0xFF999999), modifier = Modifier.clickable { onDismiss() })
                    }
                    Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color(0xFFEEEEEE)))
                    val linkCtx = androidx.compose.ui.platform.LocalContext.current
                    Box(Modifier.weight(1f).fillMaxSize().padding(horizontal = 12.dp)) {
                        com.qingmo.app.xiaomo.ui.XiaoMoChatPanel(
                            userId = userId,
                            dramaContext = dramaContext,
                            externalMessages = persistentMessages,
                            onLinkClick = {
                                android.widget.Toast.makeText(linkCtx, "跳转到剧集...", android.widget.Toast.LENGTH_SHORT).show()
                                onDismiss()
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun countCommentsDeep(comments: List<Map<String, Any>>): Int {
    var total = 0
    for (c in comments) {
        total += 1
        val replies = c["replies"] as? List<Map<String, Any>> ?: emptyList()
        total += countCommentsDeep(replies)
    }
    return total
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun DiscussionSheet(
    visible: Boolean,
    episodeId: Long,
    userId: String,
    scope: CoroutineScope,
    onDismiss: () -> Unit,
    onCommentPosted: () -> Unit = {},
    onNavigateToDrama: (Int) -> Unit = {},
) {
    val ctx = LocalContext.current
    var comments by remember(episodeId) { mutableStateOf(listOf<Map<String, Any>>()) }
    var inputText by remember(episodeId) { mutableStateOf("") }
    var replyTargetNick by remember(episodeId) { mutableStateOf("") }
    var replyParentId by remember(episodeId) { mutableStateOf(0) }
    var replyTargetIsTopLevel by remember(episodeId) { mutableStateOf(true) }
    var expandMap by remember(episodeId) { mutableStateOf(mutableMapOf<Int, Boolean>()) }
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboardCtrl = LocalSoftwareKeyboardController.current
    var likeCounts by remember(episodeId) { mutableStateOf(mutableMapOf<Int, Int>()) }
    var likedSet by remember(episodeId) { mutableStateOf(mutableSetOf<Int>()) }

    LaunchedEffect(episodeId, visible) {
        if (visible && episodeId > 0) {
            comments = emptyList()
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                try {
                    val remoteComments = RetrofitClient.api.getComments(episodeId)
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        comments = remoteComments
                    }
                } catch (_: Exception) {}
            }
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(250)) + fadeIn(tween(250)),
        exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(200)) + fadeOut(tween(200))
    ) {
        Column(Modifier.fillMaxSize().imePadding()) {
            // 上方视频区域 — 点击关闭抽屉
            Box(Modifier.fillMaxWidth().weight(0.4f).clickable { onDismiss() })

            // 底部评论抽屉
            Surface(
                modifier = Modifier.fillMaxWidth().weight(0.6f),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                color = Color.White,
            ) {
                Column(Modifier.fillMaxSize()) {
                    // 拖拽手柄 — 向下拖拽超过30%面板高度关闭
                    var dragOffset by remember { mutableFloatStateOf(0f) }
                    val sheetHeightPx = with(LocalDensity.current) { androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp.toPx() * 0.6f }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 8.dp)
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragEnd = {
                                        if (dragOffset > sheetHeightPx * 0.3f) onDismiss()
                                        dragOffset = 0f
                                    },
                                    onDragCancel = { dragOffset = 0f },
                                    onVerticalDrag = { _, amount -> dragOffset += amount }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(Modifier.width(40.dp).height(4.dp).background(Color(0xFFDDDDDD), RoundedCornerShape(2.dp)))
                    }
                    // ---- 标题栏 ----
                    Row(
                        Modifier.fillMaxWidth().height(48.dp).padding(start = 16.dp, end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val totalAll = remember(comments) { countCommentsDeep(comments) }
                        Text("${totalAll}条评论", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF333333), modifier = Modifier.weight(1f))
                        Text("✕", fontSize = 20.sp, color = Color(0xFF999999), modifier = Modifier.clickable { onDismiss() })
                    }
                    Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color(0xFFEEEEEE)))

                // ---- 评论列表 ----
                if (comments.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("还没有评论，快来抢沙发吧~", fontSize = 14.sp, color = Color(0xFF999999))
                    }
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f)) {
                        items(comments.size) { idx ->
                            val c = comments[idx]
                            val cid = (c["id"] as? Number)?.toInt() ?: idx
                            val commentUserId = (c["user_id"] as? String) ?: ""
                            val nickname = c["nickname"] as? String ?: "热心网友"
                            val text = c["text"] as? String ?: ""
                            val created = c["created_at"] as? String ?: ""
                            val replies = (c["replies"] as? List<Map<String, Any>>) ?: emptyList()
                            val isExpanded = expandMap[cid] == true
                            val likeCount = likeCounts[cid] ?: 0
                            val liked = cid in likedSet
                            val showTime = if (created.length >= 5) created.substring(5, 10) else created
                            val repliesMine = replies.map { (it["user_id"] as? String ?: "") == userId }

                            // 顶级评论（isTopLevel=true，不附加前缀）
                            CommentItem(
                                id = cid, nickname = nickname, text = text,
                                replyToUserName = null,
                                showTime = showTime, likeCount = likeCount, liked = liked,
                                replies = replies, isExpanded = isExpanded,
                                isMine = commentUserId == userId,
                                isXiaomo = commentUserId == "xiaomo_agent",
                                repliesMine = repliesMine,
                                likeCounts = likeCounts, likedSet = likedSet,
                                expandMap = expandMap,
                                onReply = { targetCommentId, targetNickname, isTopLevelTarget ->
                                replyTargetNick = targetNickname
                                replyParentId = targetCommentId
                                replyTargetIsTopLevel = isTopLevelTarget
                                inputText = ""
                                keyboardCtrl?.show()
                                focusRequester.requestFocus()
                            },
                                onLike = { likeId: Int, cur: Boolean ->
                                    if (cur) { likeCounts[likeId] = ((likeCounts[likeId] ?: 0) - 1).coerceAtLeast(0); likedSet.remove(likeId) }
                                    else { likeCounts[likeId] = (likeCounts[likeId] ?: 0) + 1; likedSet.add(likeId) }
                                },
                                onToggleExpand = { expandId: Int ->
                                    expandMap = expandMap.toMutableMap().also { it[expandId] = !(it[expandId] == true) }
                                },
                            )
                            // 分割线
                            if (idx != comments.size - 1) {
                                Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color(0xFFEEEEEE)).padding(start = 16.dp, end = 16.dp))
                            }
                        }
                    }
                }

                // ---- 底部输入栏 ----
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color(0xFFEEEEEE)))
                Row(
                    Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f).height(36.dp).background(Color(0xFFF5F5F5), RoundedCornerShape(20.dp)).padding(horizontal = 12.dp)
                            .focusRequester(focusRequester),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF333333), fontSize = 14.sp),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (inputText.isEmpty()) {
                                    val hint = if (replyTargetNick.isNotEmpty())
                                        "@$replyTargetNick："
                                    else
                                        "输入评论，@小墨 可以问我剧情问题~"
                                    Text(hint, fontSize = 14.sp, color = Color(0xFFBBBBBB), maxLines = 1)
                                }
                                inner()
                            }
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("发布", fontSize = 14.sp, color = if (inputText.trim().isNotEmpty()) Color(0xFF1E88E5) else Color(0xFFBBBBBB), fontWeight = FontWeight.Medium, modifier = Modifier.clickable(enabled = inputText.trim().isNotEmpty()) {
                        val pureText = inputText.trim()
                        if (pureText.isEmpty()) return@clickable
                        val isAtXiaomo = pureText.startsWith("@小墨", ignoreCase = true)
                        scope.launch(Dispatchers.IO) {
                            try {
                                val resp = RetrofitClient.api.postComment(episodeId, mapOf(
                                    "user_id" to com.qingmo.app.data.auth.TokenManager.getUserId().toString(),
                                    "text" to pureText,
                                    "parent_id" to replyParentId,
                                    "reply_to_nickname" to if (replyTargetIsTopLevel) "" else replyTargetNick
                                ))
                                val commentId = (resp["id"] as? Number)?.toInt() ?: 0
                                comments = RetrofitClient.api.getComments(episodeId)
                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                    if (replyParentId > 0) {
                                        expandMap = expandMap.toMutableMap().also { it[replyParentId] = true }
                                    }
                                    inputText = ""; replyTargetNick = ""; replyParentId = 0; replyTargetIsTopLevel = true
                                    keyboardCtrl?.hide()
                                    onCommentPosted()
                                    android.widget.Toast.makeText(ctx, "发送成功", android.widget.Toast.LENGTH_SHORT).show()
                                }
                                // @小墨 轮询等待AI回复
                                if (isAtXiaomo && commentId > 0) {
                                    launch(Dispatchers.IO) {
                                        repeat(6) {
                                            kotlinx.coroutines.delay(2000)
                                            try {
                                                val status = RetrofitClient.api.getAiReplyStatus(episodeId, commentId.toString())
                                                val replies = status["replies"] as? Map<*, *>
                                                if (replies != null && replies.containsKey(commentId.toString())) {
                                                    comments = RetrofitClient.api.getComments(episodeId)
                                                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                                                        expandMap = expandMap.toMutableMap().also { it[commentId] = true }
                                                    }
                                                    return@repeat
                                                }
                                            } catch (_: Exception) {}
                                        }
                                    }
                                }
                            } catch (_: Exception) {
                                kotlinx.coroutines.withContext(Dispatchers.Main) { android.widget.Toast.makeText(ctx, "发送失败", android.widget.Toast.LENGTH_SHORT).show() }
                            }
                        }
                    })
                }
            }
        }
    }
}

@Composable
fun CharacterChatDialog(
    dramaId: Int,
    userId: String,
    onDismiss: () -> Unit,
) {
    var characters by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var selectedChar by remember { mutableStateOf<Map<String, Any>?>(null) }
    var inputText by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(dramaId) {
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                characters = RetrofitClient.api.listCharacters(dramaId)
            } catch (_: Exception) {}
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable { if (selectedChar == null) onDismiss() }) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.75f).align(Alignment.BottomCenter),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = Color.White,
        ) {
            Column(Modifier.fillMaxSize().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (selectedChar != null) "💬 ${(selectedChar!!["name"] as? String ?: "")}" else "🎭 选择角色",
                        fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    )
                    TextButton(onClick = { if (selectedChar != null) selectedChar = null else onDismiss() }) {
                        Text(if (selectedChar != null) "返回" else "关闭", color = Color(0xFF999999))
                    }
                }
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color(0xFFEEEEEE)))

                if (selectedChar != null) {
                    val char = selectedChar!!
                    androidx.compose.foundation.lazy.LazyColumn(
                        Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        reverseLayout = true,
                    ) {
                        val reversed = messages.reversed()
                        items(reversed.size) { idx ->
                            val msg = reversed[idx]
                            val isUser = msg.first == "user"
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isUser) Color(0xFF1E88E5) else Color(0xFFF0F0F0),
                                ) {
                                    androidx.compose.material3.Text(msg.second, fontSize = 14.sp, color = if (isUser) Color.White else Color(0xFF333333), modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                                }
                            }
                        }
                    }

                    Row(Modifier.fillMaxWidth().padding(12.dp).background(Color.White), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier.weight(1f).height(40.dp).background(Color(0xFFF5F5F5), RoundedCornerShape(20.dp)).padding(horizontal = 12.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF333333), fontSize = 14.sp),
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (inputText.isEmpty()) Text("和${char["name"]}说点什么...", color = Color(0xFFBBBBBB), fontSize = 14.sp)
                                    innerTextField()
                                }
                            },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("发送", color = if (inputText.isNotBlank() && !loading) Color(0xFF1E88E5) else Color(0xFFBBBBBB), fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clickable(enabled = inputText.isNotBlank() && !loading) {
                            val msg = inputText.trim()
                            if (msg.isEmpty()) return@clickable
                            messages = messages + ("user" to msg)
                            inputText = ""
                            loading = true
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val charId = (char["id"] as? Number)?.toInt() ?: return@launch
                                    val resp = RetrofitClient.api.characterChat(charId, mapOf("user_message" to msg, "drama_id" to dramaId))
                                    val reply = resp["reply"] as? String ?: "（对方暂时不在线）"
                                    messages = messages + ("char" to reply)
                                } catch (_: Exception) {
                                    messages = messages + ("char" to "（连接失败，稍后再试）")
                                }
                                loading = false
                            }
                        })
                    }
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        items(characters.size) { idx ->
                            val c = characters[idx]
                            val name = c["name"] as? String ?: ""
                            val role = c["role"] as? String ?: ""
                            val desc = c["description"] as? String ?: ""
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFF8F8F8),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { selectedChar = c },
                            ) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Surface(shape = CircleShape, color = Color(0xFF7C4DFF), modifier = Modifier.size(44.dp)) {
                                        Box(contentAlignment = Alignment.Center) { Text(name.first().uppercase(), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF333333))
                                        if (role.isNotEmpty()) Text(role, fontSize = 12.sp, color = Color(0xFF999999))
                                        if (desc.isNotEmpty()) Text(desc, fontSize = 11.sp, color = Color(0xFFBBBBBB), maxLines = 1, modifier = Modifier.padding(top = 2.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
} 
