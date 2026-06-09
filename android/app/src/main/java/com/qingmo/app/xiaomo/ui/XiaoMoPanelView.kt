package com.qingmo.app.xiaomo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingmo.app.R
import com.qingmo.app.ui.theme.Background
import com.qingmo.app.ui.theme.Border
import com.qingmo.app.ui.theme.OnSurface
import com.qingmo.app.ui.theme.OnSurfaceMuted
import com.qingmo.app.ui.theme.Primary
import com.qingmo.app.ui.theme.OnPrimary
import com.qingmo.app.xiaomo.XiaoMoPose

/**
 * 小墨 Expanded 状态 — 完整形象 + 互动面板
 *
 * 尺寸：280dp × 400dp
 * 入场：右侧滑入 300ms
 * 退出：右侧滑出 250ms
 * 背景：半透明毛玻璃效果（青白底色 + 透明度）
 */
@Composable
fun XiaoMoPanelView(
    visible: Boolean,
    title: String = "小墨",
    onClose: () -> Unit,
    children: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    pose: XiaoMoPose = XiaoMoPose.Idle,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 300),
        ),
        exit = slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 250),
        ),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .width(280.dp)
                .height(400.dp)
                .background(
                    Background.copy(alpha = 0.95f),
                    RoundedCornerShape(20.dp),
                )
                .then(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Border.copy(alpha = 0.3f),
                            RoundedCornerShape(20.dp),
                        ),
                ),
        ) {
            // 关闭按钮悬浮在右上角
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 10.dp)
                    .size(32.dp)
                    .background(
                        Border.copy(alpha = 0.5f),
                        CircleShape,
                    ),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "关闭",
                    tint = OnSurface,
                    modifier = Modifier.size(16.dp),
                )
            }

            // 聊天界面铺满全部区域
            Column(
                modifier = Modifier
                    .matchParentSize()
                    .padding(top = 52.dp, start = 12.dp, end = 12.dp, bottom = 12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    children?.invoke()
                }
            }
        }
    }
}
