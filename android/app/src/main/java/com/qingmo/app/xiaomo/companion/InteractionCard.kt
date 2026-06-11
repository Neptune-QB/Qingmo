package com.qingmo.app.xiaomo.companion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** 统一互动卡片容器 — 白色半透明 + 圆角16dp + 统一阴影 */
@Composable
fun InteractionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .widthIn(max = 240.dp)
            .shadow(12.dp, RoundedCornerShape(16.dp), ambientColor = Color.Black.copy(alpha = 0.28f))
            .background(Color.White.copy(alpha = 0.94f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        content = content,
    )
}

/** 卡片标题 */
@Composable
fun CardTitle(text: String) {
    androidx.compose.material3.Text(
        text = text,
        color = Color(0xFF111111),
        fontSize = androidx.compose.ui.unit.TextUnit(13f, androidx.compose.ui.unit.TextUnitType.Sp),
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        lineHeight = androidx.compose.ui.unit.TextUnit(18f, androidx.compose.ui.unit.TextUnitType.Sp),
        maxLines = 2,
    )
}

/** 卡片描述 */
@Composable
fun CardDesc(text: String) {
    if (text.isNotEmpty()) {
        androidx.compose.material3.Text(
            text = text,
            color = Color(0xFF666666),
            fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp),
            lineHeight = androidx.compose.ui.unit.TextUnit(16f, androidx.compose.ui.unit.TextUnitType.Sp),
            maxLines = 2,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
