package com.qingmo.app.xiaomo

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import kotlin.random.Random

/**
 * 高光互动按钮 — 通用组件。
 *
 * 根据 interactionKey 从 assets/xiaomo_interaction/{key}/ 加载按钮和上浮图标。
 */
class InteractionButton(
    context: Context,
    private val floatingParent: android.view.ViewGroup,
    private val interactionKey: String = "thrill",
) : View(context) {

    var onInteraction: ((count: Int) -> Unit)? = null

    private val density = context.resources.displayMetrics.density
    private val handler = Handler(Looper.getMainLooper())

    private var comboCount = 0
    private var resetRunnable: Runnable? = null
    private var _interactionKey = interactionKey

    // 位图
    private var buttonBitmap: android.graphics.Bitmap? = null
    private var iconBitmap: android.graphics.Bitmap? = null

    // ×N 文本
    private var comboText: TextView? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    init {
        loadBitmaps()
        isClickable = true
        isFocusable = true

        setOnClickListener {
            Log.d("InteractionBtn", "onClick! key=$_interactionKey comboCount=$comboCount")
            performClickWithEffect()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        buttonBitmap?.let { bm ->
            val dst = Rect(0, 0, width, height)
            val src = Rect(0, 0, bm.width, bm.height)
            canvas.drawBitmap(bm, src, dst, paint)
        }
    }

    fun setInteractionKey(key: String) {
        if (_interactionKey == key) return
        _interactionKey = key
        buttonBitmap = null
        iconBitmap = null
        loadBitmaps()
        invalidate()
    }

    private fun loadBitmaps() {
        try {
            context.assets.open("xiaomo_interaction/$_interactionKey/${_interactionKey}_button.png").use { s ->
                buttonBitmap = BitmapFactory.decodeStream(s)
            }
        } catch (_: Exception) { buttonBitmap = null }
        try {
            context.assets.open("xiaomo_interaction/$_interactionKey/${_interactionKey}_icon.png").use { s ->
                iconBitmap = BitmapFactory.decodeStream(s)
            }
        } catch (_: Exception) { iconBitmap = null }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = super.onTouchEvent(event)
        Log.d("InteractionBtn", "onTouchEvent key=$interactionKey action=${event.action} handled=$handled")
        return handled
    }

    fun getButtonBmp() = buttonBitmap
    fun getIconBmp() = iconBitmap

    fun performClickWithEffect() {
        val count = ++comboCount
        Log.d("InteractionBtn", "performClickWithEffect key=$interactionKey count=$count")
        playPressAnimation()
        spawnFloatingIcon()
        updateComboText(count)
        restartResetTimer()
        onInteraction?.invoke(count)
    }

    fun resetCombo() {
        Log.d("InteractionBtn", "resetCombo key=$interactionKey")
        comboCount = 0
        resetRunnable?.let { handler.removeCallbacks(it) }
        resetRunnable = null
        comboText?.let {
            it.animate().cancel()
            it.visibility = GONE
        }
    }

    private fun playPressAnimation() {
        animate().cancel()
        animate()
            .scaleX(0.88f).scaleY(0.88f)
            .setDuration(70)
            .withEndAction {
                animate()
                    .scaleX(1f).scaleY(1f)
                    .setDuration(100)
                    .start()
            }
            .start()
    }

    private fun spawnFloatingIcon() {
        val bm = iconBitmap ?: return
        val icon = ImageView(context).apply {
            setImageBitmap(bm)
            scaleType = ImageView.ScaleType.FIT_CENTER
            scaleX = 0.85f
            scaleY = 0.85f
            alpha = 1f
        }

        val btnLoc = IntArray(2)
        this.getLocationInWindow(btnLoc)
        val parentLoc = IntArray(2)
        floatingParent.getLocationInWindow(parentLoc)

        val btnCenterX = btnLoc[0] - parentLoc[0] + width / 2
        val btnTop = btnLoc[1] - parentLoc[1]
        val iconSize = dp(60f)
        val lp = FrameLayout.LayoutParams(iconSize, iconSize).apply {
            leftMargin = btnCenterX - iconSize / 2
            topMargin = btnTop - iconSize / 2
        }
        floatingParent.addView(icon, lp)

        val randomX = dp(Random.nextFloat() * 32f - 16f).toFloat()
        val randomY = dp(80f + Random.nextFloat() * 60f).toFloat()
        val randomRot = Random.nextFloat() * 16f - 8f
        val duration = 650L + Random.nextLong(251L)

        icon.animate()
            .translationX(randomX)
            .translationY(-randomY)
            .scaleX(1.15f)
            .scaleY(1.15f)
            .alpha(0f)
            .rotation(randomRot)
            .setDuration(duration)
            .withEndAction { floatingParent.removeView(icon) }
            .start()
    }

    private fun updateComboText(count: Int) {
        if (comboText == null) {
            comboText = TextView(context).apply {
                setTextColor(0xFFFFFFFF.toInt())
                setShadowLayer(dp(3f).toFloat(), 0f, dp(1.5f).toFloat(), 0xCC000000.toInt())
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 28f)
                setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
                gravity = Gravity.CENTER
            }
            val ctLp = FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply {
                leftMargin = dp(16f) + dp(80f) + dp(4f)
                bottomMargin = dp(177f) + dp(80f) + dp(4f)
            }
            floatingParent.addView(comboText, ctLp)
        }
        comboText?.apply {
            text = "\u00D7$count"
            visibility = VISIBLE
            animate().cancel()
            scaleX = 0.6f
            scaleY = 0.6f
            alpha = 1f
            animate()
                .scaleX(1.4f).scaleY(1.4f)
                .setDuration(150)
                .withEndAction {
                    animate()
                        .scaleX(1f).scaleY(1f)
                        .setDuration(100)
                        .start()
                }
                .start()
        }
    }

    private fun restartResetTimer() {
        resetRunnable?.let { handler.removeCallbacks(it) }
        val r = Runnable {
            Log.d("InteractionBtn", "resetTimer fired, hiding combo")
            comboText?.animate()?.alpha(0f)?.setDuration(300)
                ?.withEndAction {
                    comboCount = 0
                    comboText?.visibility = GONE
                }
                ?.start()
        }
        resetRunnable = r
        handler.postDelayed(r, 1100L)
    }

    private fun dp(v: Float) = (v * density + 0.5f).toInt()
}
