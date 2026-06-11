package com.qingmo.app.xiaomo.companion

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** support_button 面板 */
@Composable
fun SupportButtonPanel(
    title: String,
    description: String,
    buttonText: String,
    clickedText: String,
    isClicked: Boolean,
    onClick: () -> Unit,
) {
    val bgColor by animateColorAsState(
        targetValue = if (isClicked) Color(0xFF22C55E) else Color(0xFF111111),
        animationSpec = tween(200),
    )
    val scale by animateFloatAsState(
        targetValue = if (isClicked) 1f else 1f,
        animationSpec = tween(100),
    )

    InteractionCard {
        CardTitle(title)
        if (description.isNotEmpty()) CardDesc(description)

        // 按钮
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .height(36.dp)
                .scale(scale)
                .clip(RoundedCornerShape(999.dp))
                .background(bgColor)
                .then(if (!isClicked) Modifier else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Text(
                text = if (isClicked) "✓  $clickedText" else buttonText,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** reaction_panel 面板 */
@Composable
fun ReactionPanel(
    title: String,
    options: List<Pair<String, String>>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
) {
    var hasSelected = selectedKey != null
    val selectedLabel = if (hasSelected) options.find { it.first == selectedKey }?.second ?: "" else ""
    val percent = rememberSelectedPercent(selectedKey)

    InteractionCard {
        CardTitle(title)

        // Chip 选项
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for ((key, label) in options.take(3)) {
                val isSel = selectedKey == key
                val chipBg by animateColorAsState(
                    targetValue = if (isSel) Color(0xFF111111) else Color.White,
                    animationSpec = tween(200),
                )
                val chipBorder by animateColorAsState(
                    targetValue = if (isSel) Color(0xFF111111) else Color(0xFFD9D9D9),
                    animationSpec = tween(200),
                )
                val chipText by animateColorAsState(
                    targetValue = if (isSel) Color.White else Color(0xFF111111),
                    animationSpec = tween(200),
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(30.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .then(
                            if (isSel) Modifier.background(chipBg, RoundedCornerShape(999.dp))
                            else Modifier.background(chipBg, RoundedCornerShape(999.dp))
                        )
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Text(
                        text = label,
                        color = chipText,
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                }
            }
        }

        // 统计条（选中后显示）
        if (hasSelected) {
            val barWidth by animateFloatAsState(percent / 100f, tween(600))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.Text(
                    text = selectedLabel,
                    color = Color(0xFF666666),
                    fontSize = 11.sp,
                    modifier = Modifier.width(52.dp),
                    maxLines = 1,
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp)
                        .padding(horizontal = 6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFFEEEEEE)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(barWidth)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0xFF111111)),
                    )
                }
                androidx.compose.material3.Text(
                    text = "${percent}%",
                    color = Color(0xFF666666),
                    fontSize = 11.sp,
                    modifier = Modifier.width(32.dp),
                )
            }
        }
    }
}

@Composable
private fun rememberSelectedPercent(key: String?): Int {
    if (key == null) return 0
    // 基于 key 生成稳定的伪随机百分比
    val hash = key.hashCode().let { if (it < 0) -it else it }
    return 55 + (hash % 28)  // 55-82%
}
