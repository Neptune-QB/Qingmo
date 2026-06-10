package com.qingmo.app.xiaomo

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.util.Log
import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.qingmo.app.R

fun getXiaomoGifResId(code: String): Int {
    return when (code) {
        "cliffhanger" -> R.raw.xiaomo_cliffhanger
        "choice_point" -> R.raw.xiaomo_choice_point
        "emotional_burst" -> R.raw.xiaomo_emotional_burst
        "power_moment" -> R.raw.xiaomo_power_moment
        "comedy" -> R.raw.xiaomo_comedy
        "suspense" -> R.raw.xiaomo_suspense
        "heartbreak" -> R.raw.xiaomo_heartbreak
        "sweet_moment" -> R.raw.xiaomo_sweet_moment
        "reversal" -> R.raw.xiaomo_reversal
        "slapback" -> R.raw.xiaomo_slapback
        "idle" -> R.raw.xiaomo_idle
        else -> R.raw.xiaomo_idle
    }
}

/**
 * 单层原生 GIF 组件 — 用 AndroidView(ImageView) + ImageDecoder + AnimatedImageDrawable
 * resId 变化时，key(resId) 强制销毁重建，确保切换立即生效
 */
@Composable
fun StableXiaomoGifByResId(
    resId: Int,
    code: String = "",
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current

    Log.d("XiaomoGifDebug", "StableXiaomoGifByResId code=$code resId=$resId")

    // key(resId) 确保 resId 变化时完全销毁重建 ImageView + Drawable
    key(resId) {
        val source = remember {
            Log.d("XiaomoGifDebug", "create ImageDecoder source for resId=$resId")
            ImageDecoder.createSource(context.resources, resId)
        }
        val drawable = remember {
            val d = ImageDecoder.decodeDrawable(source)
            Log.d("XiaomoGifDebug", "decoded drawable resId=$resId drawable=${d?.javaClass?.simpleName}")
            if (d is AnimatedImageDrawable) {
                Log.d("XiaomoGifDebug", "AnimatedImageDrawable START resId=$resId")
                d.start()
            } else {
                Log.e("XiaomoGifDebug", "NOT AnimatedImageDrawable! resId=$resId actual=${d?.javaClass?.name}")
            }
            d
        }

        DisposableEffect(resId) {
            onDispose {
                Log.d("XiaomoGifDebug", "dispose drawable resId=$resId")
                (drawable as? AnimatedImageDrawable)?.stop()
            }
        }

        AndroidView(
            factory = { ctx ->
                ImageView(ctx).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setImageDrawable(drawable)
                    onClick?.let { cb -> setOnClickListener { cb() } }
                }
            },
            modifier = modifier,
        )
    }
}

/**
 * 便捷版：传 code 字符串，自动映射 resId
 */
@Composable
fun StableXiaomoGif(
    code: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val resId = remember(code) { getXiaomoGifResId(code) }
    StableXiaomoGifByResId(resId = resId, code = code, modifier = modifier, onClick = onClick)
}
