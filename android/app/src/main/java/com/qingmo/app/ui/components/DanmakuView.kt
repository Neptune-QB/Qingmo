package com.qingmo.app.ui.components

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.random.Random

data class DanmakuItem(
    val id: Long,
    val text: String,
    val timeSec: Float = 0f,
    val color: Int = Color.WHITE,
    val userId: String = "",
    val interactionType: String = "",
)

class DanmakuView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
    private var enabled = true
    private var currentUserId = ""
    private val lanes = 5
    private val laneHeight = 48f
    private val speedMin = 8000L
    private val speedMax = 12000L
    private val maxConcurrentDanmaku = 1
    private val handler = Handler(Looper.getMainLooper())
    private val density = resources.displayMetrics.density
    private var viewWidth = 0
    private val laneOccupiedUntil = LongArray(lanes)
    private val activeAnimators = mutableListOf<ObjectAnimator>()
    private var paused = false
    private val safePaddingDp = 20f
    private val safePaddingPx = (safePaddingDp * density + 0.5f).toInt()

    private var currentTimeMs = 0L
    private val TIMEWINDOW_MS = 500L
    private val pendingDanmaku = mutableListOf<DanmakuItem>()
    private val triggeredIds = mutableSetOf<Long>()
    private val sentXiaomoTexts = mutableSetOf<String>()
    private var lastKnownWidth = 0

    // 小墨头像缓存
    private val xiaomoAvatar by lazy {
        try {
            context.assets.open("xiaomo_avatar.png").use { BitmapFactory.decodeStream(it) }
        } catch (_: Exception) { null }
    }

    fun setDanmakuEnabled(enabled: Boolean) {
        val prevEnabled = this.enabled
        this.enabled = enabled
        if (prevEnabled && !enabled) {
            for (anim in activeAnimators.toList()) anim.cancel()
            activeAnimators.clear()
            removeAllViews()
            laneOccupiedUntil.fill(0)
        }
        if (!prevEnabled && enabled) {
            triggerForCurrentTime()
        }
    }

    fun setCurrentUserId(userId: String) {
        currentUserId = userId
    }

    fun setDanmakuData(items: List<DanmakuItem>) {
        pendingDanmaku.clear()
        triggeredIds.clear()
        laneOccupiedUntil.fill(0)
        pendingDanmaku.addAll(items.sortedBy { it.timeSec })
    }

    fun addPendingDanmaku(item: DanmakuItem) {
        val processedItem = if (item.text.length > 32) item.copy(text = truncateText(item.text, 30) + "…") else item
        var inserted = false
        for (i in pendingDanmaku.indices) {
            if (pendingDanmaku[i].timeSec > processedItem.timeSec) {
                pendingDanmaku.add(i, processedItem)
                inserted = true
                break
            }
        }
        if (!inserted) pendingDanmaku.add(processedItem)
        val t = (processedItem.timeSec * 1000f).toLong()
        if (t in (currentTimeMs - TIMEWINDOW_MS)..(currentTimeMs + TIMEWINDOW_MS)) {
            triggeredIds.add(processedItem.id)
            triggerSingle(processedItem)
        }
    }

    /** 自己发的弹幕或小墨弹幕：立即追加，小墨弹幕文本级去重 */
    fun forceEmitDanmaku(item: DanmakuItem) {
        if (item.userId == "xiaomo_agent" && !sentXiaomoTexts.add(item.text)) return
        val processedItem = if (item.text.length > 32) item.copy(text = truncateText(item.text, 30) + "…") else item
        pendingDanmaku.add(processedItem)
        triggeredIds.add(processedItem.id)
        post { doAddDanmaku(processedItem, skipWait = true) }
    }

    fun updatePlaybackTime(timeMs: Long) {
        currentTimeMs = timeMs
        triggerForCurrentTime()
    }

    fun seekTo(timeMs: Long) {
        currentTimeMs = timeMs
        // 拖动进度条：先把当前所有老弹幕全部清掉，完全不会叠加
        for (anim in activeAnimators.toList()) anim.cancel()
        activeAnimators.clear()
        removeAllViews()
        laneOccupiedUntil.fill(0)
        triggeredIds.clear()
        // 立刻触发新时间窗口里的弹幕
        triggerForCurrentTime()
    }

    fun clearDanmaku() {
        for (anim in activeAnimators.toList()) anim.cancel()
        activeAnimators.clear()
        removeAllViews()
        laneOccupiedUntil.fill(0)
        pendingDanmaku.clear()
        triggeredIds.clear()
        sentXiaomoTexts.clear()
    }

    fun pauseDanmaku() {
        if (paused) return
        paused = true
        for (anim in activeAnimators.toList()) {
            if (anim.isRunning) anim.pause()
        }
    }

    fun resumeDanmaku() {
        if (!paused) return
        paused = false
        for (anim in activeAnimators.toList()) {
            if (anim.isPaused) anim.resume()
        }
    }

    private fun triggerForCurrentTime() {
        if (!enabled) return
        val windowStart = currentTimeMs - TIMEWINDOW_MS
        val windowEnd = currentTimeMs + TIMEWINDOW_MS
        pendingDanmaku.forEach { item ->
            val t = (item.timeSec * 1000f).toLong()
            if (t in windowStart..windowEnd && triggeredIds.add(item.id)) {
                triggerSingle(item)
            }
        }
    }

    private fun triggerSingle(item: DanmakuItem) {
        if (activeAnimators.size >= maxConcurrentDanmaku) return
        if (paused) return
        post {
            if (!paused) doAddDanmaku(item)
        }
    }

    private fun doAddDanmaku(item: DanmakuItem, skipWait: Boolean = false) {
        if (viewWidth <= 0) {
            viewWidth = measuredWidth
            if (viewWidth <= 0) {
                post { doAddDanmaku(item, skipWait) }
                return
            }
        }
        val usableWidth = (viewWidth - safePaddingPx * 2).coerceAtLeast(200)
        val now = System.currentTimeMillis()

        val safeGapMs = 3000L
        val lane = (0 until lanes).minByOrNull { laneOccupiedUntil[it] } ?: 0
        val waitMs = if (skipWait) 0L else (laneOccupiedUntil[lane] - now + safeGapMs).coerceAtLeast(0)

        val textColor = item.color or 0xFF000000.toInt()
        val isOwn = currentUserId.isNotEmpty() && item.userId == currentUserId
        val isXiaoMo = item.userId == "xiaomo_agent"

        val bubbleView: View = if (isXiaoMo) {
            createXiaoMoBubble(item)
        } else {
            TextView(context).apply {
                text = item.text
                setTextColor(textColor)
                textSize = 14f
                setShadowLayer(1f, 0f, 0f, Color.BLACK)
                if (isOwn) {
                    background = GradientDrawable().apply {
                        setColor(0x33FFFFFF.toInt())
                        cornerRadius = 12f * density
                        setStroke((1.5f * density).toInt(), Color.WHITE)
                    }
                    setPadding(dp2px(8f), dp2px(4f), dp2px(8f), dp2px(4f))
                }
            }
        }

        val lanePx = (lane * laneHeight * density).toInt()
        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            topMargin = lanePx
            leftMargin = safePaddingPx
        }
        addView(bubbleView, lp)

        bubbleView.measure(
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
        )
        val textW = bubbleView.measuredWidth
        val totalDist = (usableWidth + textW).toFloat()
        val duration = speedMin + (speedMax - speedMin) * Random.nextFloat()
        val scaledDuration = (duration * totalDist / usableWidth).toLong()

        val startX = viewWidth.toFloat()
        val endX = -textW.toFloat()
        bubbleView.translationX = startX

        ObjectAnimator.ofFloat(bubbleView, "translationX", startX, endX).apply {
            this.duration = scaledDuration
            interpolator = LinearInterpolator()
            startDelay = waitMs
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    removeView(bubbleView)
                    activeAnimators.remove(animation)
                }
                override fun onAnimationCancel(animation: Animator) {
                    removeView(bubbleView)
                    activeAnimators.remove(animation)
                }
            })
        }.also { anim ->
            activeAnimators.add(anim)
            val occupyDuration = 12000L
            laneOccupiedUntil[lane] = now + waitMs + occupyDuration
            if (!paused || skipWait) {
                anim.start()
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != lastKnownWidth && w > 0) {
            viewWidth = w
            lastKnownWidth = w
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacksAndMessages(null)
    }

    private fun dp2px(dp: Float): Int = (dp * density + 0.5f).toInt()

    /**
     * 创建小墨专属弹幕胶囊框：
     * [头像 22dp] [小墨] [正文]  — 深色半透胶囊 + 白描边 + 阴影
     */
    private fun createXiaoMoBubble(item: DanmakuItem): View {
        val isHighlight = item.interactionType.isNotEmpty()
        val enhanced = isHighlight

        // 背景
        val bg = GradientDrawable().apply {
            setColor(0xB7080C0E.toInt()) // rgba(8,12,14,0.72)
            cornerRadius = 999f * density
            val borderColor = if (enhanced) 0xA600D2CD.toInt() else 0x47FFFFFF.toInt()
            setStroke(dp2px(1f), borderColor)
        }

        // 直接用 FrameLayout + 背景 + elevation
        val bubble = FrameLayout(context).apply {
            background = bg
            elevation = dp2px(4f).toFloat()
            clipToPadding = false
            clipChildren = false
        }

        // 内容横向布局
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp2px(8f), dp2px(6f), dp2px(12f), dp2px(6f))
        }

        // 头像
        val avatarBmp = xiaomoAvatar
        if (avatarBmp != null) {
            val avatar = ImageView(context).apply {
                setImageBitmap(avatarBmp)
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = LinearLayout.LayoutParams(dp2px(22f), dp2px(22f)).apply {
                    setMargins(0, 0, dp2px(6f), 0)
                }
                // 圆形裁切（通过 clipToOutline + CircleShape 或直接用 background circle）
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setSize(dp2px(22f), dp2px(22f))
                }
                clipToOutline = true
            }
            content.addView(avatar)
        }

        // "小墨" 标签
        val label = TextView(context).apply {
            text = "小墨"
            setTextColor(0xFF00D2CD.toInt())
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply {
                setMargins(0, 0, dp2px(6f), 0)
            }
        }
        content.addView(label)

        // 正文（移除 "小墨: " 前缀）
        val displayText = item.text.removePrefix("小墨: ").removePrefix("小墨:")
        val body = TextView(context).apply {
            text = displayText
            setTextColor(Color.WHITE)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f)
            setShadowLayer(1f, 0f, 0f, 0x66000000.toInt())
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        content.addView(body)

        bubble.addView(
            content,
            FrameLayout.LayoutParams(-2, dp2px(36f)).apply {
                gravity = Gravity.CENTER_VERTICAL
            },
        )

        // 高光弹入动画
        if (enhanced) {
            bubble.scaleX = 0.92f
            bubble.scaleY = 0.92f
            bubble.alpha = 0f
            bubble.animate()
                .scaleX(1.04f).scaleY(1.04f).alpha(1f)
                .setDuration(120)
                .withEndAction {
                    bubble.animate()
                        .scaleX(1f).scaleY(1f)
                        .setDuration(60)
                        .start()
                }
                .start()
        }

        return bubble
    }

    companion object {
        // 断句标点：在这些位置自然截断，不会切在词语中间
        private val BREAK_CHARS = setOf('，', '。', '！', '？', '、', '；', '：', '…', ')', '】', '》')

        /**
         * 智能截断文本：在 [maxLen] 范围内寻找最近的标点断句处，
         * 若找不到自然断点则退回到硬截断，避免切断词语。
         */
        fun truncateText(text: String, maxLen: Int): String {
            if (text.length <= maxLen) return text
            // 从 maxLen 位置向前搜索最近的自然断句点，最小保留 8 个字
            for (i in maxLen downTo maxLen.coerceAtMost(8)) {
                if (text[i] in BREAK_CHARS) {
                    return text.substring(0, i + 1).trimEnd()
                }
            }
            return text.substring(0, maxLen)
        }
    }
}
