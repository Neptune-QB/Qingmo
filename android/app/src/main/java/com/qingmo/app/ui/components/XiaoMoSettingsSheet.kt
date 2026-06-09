package com.qingmo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingmo.app.xiaomo.XiaoMoSettings

@Composable
fun XiaoMoSettingsSheet(onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { onDismiss() }) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(0.7f).align(Alignment.BottomCenter), shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), color = Color.White) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("⚙ 小墨功能设置", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF333333))
                    Row {
                        TextButton(onClick = { XiaoMoSettings.resetAll(); onDismiss() }) { Text("恢复默认", fontSize = 13.sp, color = Color(0xFF999999)) }
                        TextButton(onClick = onDismiss) { Text("完成", fontSize = 14.sp, color = Color(0xFF1E88E5)) }
                    }
                }
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color(0xFFEEEEEE)))
                LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    items(XiaoMoSettings.FEATURES.size) { idx ->
                        val feat = XiaoMoSettings.FEATURES[idx]
                        val key = feat.first; val label = feat.second
                        var enabled by remember { mutableStateOf(XiaoMoSettings.isEnabled(key)) }
                        Row(Modifier.fillMaxWidth().height(48.dp).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(label, fontSize = 15.sp, color = Color(0xFF333333))
                            Switch(checked = enabled, onCheckedChange = { v -> enabled = v; XiaoMoSettings.setEnabled(key, v) })
                        }
                        if (idx < XiaoMoSettings.FEATURES.size - 1) Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color(0xFFF0F0F0)))
                    }
                }
            }
        }
    }
}
