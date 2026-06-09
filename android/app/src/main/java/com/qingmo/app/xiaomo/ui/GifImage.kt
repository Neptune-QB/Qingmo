package com.qingmo.app.xiaomo.ui

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/**
 * 原生 GIF 动画组件 — Android ImageDecoder + AnimatedImageDrawable
 */
@Composable
fun GifImage(
    resId: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val source = remember { ImageDecoder.createSource(context.resources, resId) }
    val drawable = remember { ImageDecoder.decodeDrawable(source) as? AnimatedImageDrawable }

    DisposableEffect(drawable) {
        drawable?.start()
        onDispose { drawable?.stop() }
    }

    AndroidView(
        factory = { ctx ->
            ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageDrawable(drawable)
                onClick?.let { cb ->
                    setOnClickListener { cb() }
                }
            }
        },
        modifier = modifier,
    )
}
