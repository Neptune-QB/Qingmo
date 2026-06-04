package com.qingmo.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Indication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.outlined.Comment
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.activity.compose.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingmo.app.ui.theme.*

@Composable
fun OverlayInput(
    visible: Boolean,
    placeholder: String,
    danmakuVisible: Boolean,
    onDanmakuToggle: () -> Unit,
    maxLength: Int = 80,
    onSend: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember(visible) { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // 监听键盘是否被系统收起（返回键/手势），自动关闭浮层
    val imeBottomDp = with(LocalDensity.current) { WindowInsets.ime.getBottom(this).toDp() }
    var keyboardWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(imeBottomDp, visible) {
        if (visible) {
            if (imeBottomDp > 0.dp) {
                keyboardWasOpen = true
            } else if (keyboardWasOpen && imeBottomDp <= 0.dp) {
                // 键盘已收起 → 自动关闭输入浮层
                keyboardWasOpen = false
                onDismiss()
            }
        } else {
            keyboardWasOpen = false
        }
    }

    LaunchedEffect(visible) {
        if (visible) {
            kotlinx.coroutines.delay(100)
            focusRequester.requestFocus()
            keyboard?.show()
        } else {
            keyboard?.hide()
        }
    }

    val imeBottom = imeBottomDp  // 复用上方已声明的 imeBottomDp
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        BackHandler { onDismiss() }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = imeBottomDp)
                .background(Surface)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (danmakuVisible) Icons.Default.Comment else Icons.Outlined.Comment,
                contentDescription = "弹幕开关",
                tint = if (danmakuVisible) Primary else OnSurfaceMuted,
                modifier = Modifier
                    .size(28.dp)
                    .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { onDanmakuToggle() }
                    .padding(4.dp)
            )
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = text,
                onValueChange = { if (it.length <= maxLength) text = it },
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceVariant)
                    .padding(horizontal = 12.dp)
                    .focusRequester(focusRequester),
                textStyle = TextStyle(color = OnSurface, fontSize = 14.sp, lineHeight = 20.sp),
                cursorBrush = SolidColor(Primary),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (text.isEmpty()) Text(
                            placeholder,
                            color = OnSurfaceMuted,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                        )
                        inner()
                    }
                },
                singleLine = true,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "发送",
                color = if (text.isNotBlank()) Primary else Primary.copy(alpha = 0.4f),
                fontSize = 14.sp,
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .clickable(enabled = text.isNotBlank()) {
                        onSend(text.trim())
                        text = ""
                    },
            )
            Text(
                "取消",
                color = OnSurfaceVariant,
                fontSize = 14.sp,
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .clickable {
                        onDismiss()
                        text = ""
                    },
            )
        }
    }
}
