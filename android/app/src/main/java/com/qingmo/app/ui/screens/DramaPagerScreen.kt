package com.qingmo.app.ui.screens

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.qingmo.app.data.ProgressCache
import com.qingmo.app.data.api.RetrofitClient
import com.qingmo.app.data.model.DramaBrief
import com.qingmo.app.data.model.DramaDetail
import com.qingmo.app.data.model.EpisodeBrief
import com.qingmo.app.data.repository.DramaRepository
import com.qingmo.app.ui.theme.PlayerAccent
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
    val density = LocalDensity.current.density
    val dpx: (Float) -> Int = { (it * density + 0.5f).toInt() }
    val sorted = remember(detail.episodes) { detail.episodes.sortedBy { it.episodeNum } }
    val startIdx =
        remember(startId, sorted) { sorted.indexOfFirst { it.episodeId == startId }.coerceIn(0, sorted.lastIndex) }
    var curPage by remember { mutableIntStateOf(startIdx) }
    var danmaku by remember { mutableStateOf(true) }
    var rate by remember { mutableFloatStateOf(1.0f) }
    var showEps by remember { mutableStateOf(false) }
    var showSpd by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }
    var curPos by remember { mutableLongStateOf(0L) }
    var curDur by remember { mutableLongStateOf(0L) }
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
            ).also { it.onFullscreenChange = { fs -> fullscreen = fs } }
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
    }
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
) : RecyclerView.Adapter<NativeAdapter.VH>() {
    val players = mutableMapOf<Int, ExoPlayer>()
    var activePlayer: ExoPlayer? = null
    var viewPager2: ViewPager2? = null // Reference for touch disallow during seekbar drag
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val progressJobs = mutableMapOf<Int, Job>()
    private val d = dm.density

    private fun dp(v: Float) = (v * d + 0.5f).toInt()

    private var activeVh: VH? = null
    private var isFullscreen = false
    private val screenH = dm.heightPixels
    private val topGradH = (screenH * 0.22f).toInt()
    private val botGradH = (screenH * 0.36f).toInt()

    class VH(
        val root: FrameLayout,
        val pv: PlayerView,
        val titleTv: TextView,
        val speedTv: TextView,
        val descTv: TextView,
        val danmakuBtn: TextView,
        val seekBar: SeekBar,
        val timeLabel: TextView,
        val topBar: View,
        val rightBar: View,
        val bottomInfo: View,
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
                layoutParams = FrameLayout.LayoutParams(-1, -1)
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
            players[cur]?.let {
                it.playWhenReady = !it.playWhenReady
                playIcon.visibility =
                    if (it.playWhenReady) View.GONE else View.VISIBLE
            }
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
        val tb =
            LinearLayout(c).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16f), dp(10f), dp(16f), dp(10f))
                layoutParams =
                    FrameLayout.LayoutParams(-1, -2, Gravity.TOP)
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
                        ).apply { setMargins(0, 0, dp(14f), dp(130f)) }
            }
        r.addView(rb)
        rb.addView(actionItem("\u2605", "7.7\u4E07"))
        rb.addView(actionItem("\uD83D\uDCAC", "129"))
        rb.addView(actionItem("\u2665", "2387"))
        rb.addView(actionItem("\u2197", "\u5206\u4EAB"))
        val bi =
            LinearLayout(c).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16f), 0, dp(16f), 0)
                layoutParams =
                    FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { setMargins(0, 0, 0, dp(62f)) }
            }
        r.addView(bi)
        val danmakuBtn =
            makeTv("\u5F39", 13f, C_WHITE85).apply {
                setPadding(dp(8f), dp(4f), dp(8f), dp(4f))
                background =
                    GradientDrawable().apply {
                        cornerRadius = dp(6f).toFloat()
                        setColor(0x38FFFFFF)
                    }
                setOnClickListener {
                    danmaku =
                        !danmaku
                    ; updateDanmaku(this)
                }
            }
        bi.addView(danmakuBtn, LinearLayout.LayoutParams(-2, -2))
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
        val seekBar = SeekBar(c, timeLabel)
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
        return VH(r, pv, titleTv, speedTv, descTv, danmakuBtn, seekBar, timeLabel, tb, rb, bi)
    }

    override fun onBindViewHolder(
        h: VH,
        pos: Int,
    ) {
        val ep = eps[pos]
        if (pos == cur) activeVh = h
        h.titleTv.text = "\u7B2C${ep.episodeNum}\u96C6"
        h.speedTv.text = "${rate}x"
        val player =
            players[pos] ?: run {
                val sp = ProgressCache.get(ep.episodeId)
                ExoPlayer.Builder(ctx).build().apply {
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
        h.pv.player = player
        progressJobs[pos]?.cancel()
        progressJobs[pos] =
            scope.launch {
                var last = 0L
                while (true) {
                    if (player.isPlaying) {
                        val p = player.currentPosition
                        if (p -
                            last >=
                            2000
                        ) {
                            ProgressCache.save(ep.episodeId, p)
                            last = p
                        }
                    }
                    delay(200)
                }
            }
        val savedMs = ProgressCache.get(ep.episodeId)
        val dur = ep.duration * 1000L
        h.seekBar.setProgress(if (dur > 0 && savedMs > 0) savedMs.toFloat() / dur else 0f, dur)
        h.seekBar.onSeek = { player.seekTo(it) }
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
        if (pos == cur) applyFullscreenToActiveVh()
    }

    override fun onViewRecycled(holder: VH) {
        super.onViewRecycled(holder)
        val pos = holder.absoluteAdapterPosition
        if (pos != RecyclerView.NO_POSITION) {
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
        activeVh?.let { vh ->
            val vis = if (isFullscreen) View.INVISIBLE else View.VISIBLE
            vh.topBar.visibility =
                vis
            vh.rightBar.visibility = vis
            vh.bottomInfo.visibility = vis
            val bb =
                vh.root.getChildAt(
                    vh.root.childCount - 1,
                ) as? LinearLayout
            ; val fb = bb?.getChildAt(2) as? ImageView
            fb?.setImageResource(
                if (isFullscreen) {
                    com.qingmo.app.R.drawable.fullscreen_exit
                } else {
                    com.qingmo.app.R.drawable.fullscreen_enter
                },
            )
        }
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
    ) : View(context) {
        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x20FFFFFF.toInt() }
        private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x60FFFFFF.toInt() }
        private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xB3FFFFFF.toInt() }
        private var progress = 0f
        private var totalDuration = 0L
        private var isDragging = false
        private val thumbRadius = dp(4f).toFloat()
        private val trackHeight = dp(2f).toFloat()
        var onSeek: ((Long) -> Unit)? = null
        var onDragChange: ((Boolean) -> Unit)? = null
        private var seekPlayer: ExoPlayer? = null

        fun setPlayer(p: ExoPlayer?) {
            seekPlayer = p
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

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cy =
                height / 2f
            val w = width.toFloat()
            val th = if (isDragging) dp(4f).toFloat() else trackHeight
            val tr = if (isDragging) 0x40FFFFFF.toInt() else trackPaint.color
            val pr = if (isDragging) 0xE0FFFFFF.toInt() else progressPaint.color
            canvas.drawRoundRect(
                0f,
                cy - th / 2,
                w,
                cy + th / 2,
                th / 2,
                th / 2,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tr },
            )
            val px =
                w * progress
            if (px >
                0
            ) {
                canvas.drawRoundRect(
                    0f,
                    cy - th / 2,
                    px,
                    cy + th / 2,
                    th / 2,
                    th / 2,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color =
                            pr
                    },
                )
            }
            canvas.drawCircle(px, cy, if (isDragging) dp(6f).toFloat() else thumbRadius, thumbPaint)
        }

        override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    isDragging = true
                    updateProgress(event.x)
                    timeLabel.visibility =
                        View.VISIBLE
                    updateTimeLabel()
                    onDragChange?.invoke(true)
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    updateProgress(event.x)
                    updateTimeLabel()
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                    timeLabel.visibility = View.GONE
                    onDragChange?.invoke(false)
                    if (progress > 0.98f && totalDuration > 0) {
                        ProgressCache.markWatched(eps[cur].episodeId)
                        ProgressCache.save(eps[cur].episodeId, 0L)
                        onEpisodeEnd(
                            cur + 1,
                        )
                    } else {
                        val p = seekPlayer
                        val rawPos = (progress * totalDuration).toLong()
                        val pos =
                            if (totalDuration >
                                2000
                            ) {
                                rawPos.coerceAtMost(totalDuration - 500)
                            } else {
                                rawPos
                            }
                        ; p?.seekTo(pos)
                        onSeek?.invoke(pos)
                    }
                }
            }
            return true
        }

        private fun updateProgress(x: Float) {
            progress = (x / width).coerceIn(0f, 1f)
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
    Surface(onClick = onDismiss, modifier = Modifier.fillMaxSize(), color = Color.Black.copy(alpha = 0.6f)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF1A1A2E)) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "\u500D\u901F\u64AD\u653E",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(16.dp))
                    speeds.forEach { sp ->
                        val selected = abs(sp - current) < 0.01f
                        TextButton(onClick = { onSelect(sp) }, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "${sp}x",
                                color = if (selected) PlayerAccent else Color.White.copy(alpha = 0.7f),
                                fontSize = 16.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
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
