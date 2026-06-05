package com.qingmo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingmo.app.data.auth.TokenManager
import com.qingmo.app.ui.theme.*

@Composable
fun UserProfileScreen(
    onLogout: () -> Unit,
    onBack: () -> Unit,
) {
    val nickname = TokenManager.getNickname() ?: "未登录"
    val username = TokenManager.getUsername() ?: ""
    val userId = TokenManager.getUserId()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState()),
    ) {
        // 顶部栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "我的",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = OnSurface,
            )
            TextButton(onClick = onBack) {
                Text("完成", color = Primary)
            }
        }

        // 头像 + 信息卡片
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceVariant.copy(alpha = 0.3f))
                .padding(20.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                // 头像占位
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = nickname.take(1),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Primary,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(nickname, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = OnSurface)
                Text("@$username", fontSize = 13.sp, color = OnSurfaceMuted)
                Spacer(Modifier.height(4.dp))
                Text("ID: $userId", fontSize = 11.sp, color = OnSurfaceMuted)
            }
        }

        Spacer(Modifier.height(16.dp))

        // 统计数据卡片
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceVariant.copy(alpha = 0.3f))
                .padding(20.dp),
        ) {
            Column {
                Text(
                    "📊 观影数据",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OnSurface,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    StatItem("观看集数", "-")
                    StatItem("互动次数", "-")
                    StatItem("收藏短剧", "-")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 功能列表
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceVariant.copy(alpha = 0.3f))
                .padding(4.dp),
        ) {
            Column {
                ProfileMenuItem("📺 观看历史", onClick = {})
                ProfileMenuItem("❤️ 我的收藏", onClick = {})
                ProfileMenuItem("⚙️ 设置", onClick = {})
                ProfileMenuItem("❓ 关于青墨", onClick = {})
                HorizontalDivider(color = Border.copy(alpha = 0.2f), modifier = Modifier.padding(horizontal = 16.dp))
                ProfileMenuItem("🚪 退出登录", onClick = {
                    TokenManager.clear()
                    onLogout()
                }, textColor = OnSurfaceMuted)
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Primary)
        Text(label, fontSize = 12.sp, color = OnSurfaceMuted)
    }
}

@Composable
private fun ProfileMenuItem(text: String, onClick: () -> Unit, textColor: androidx.compose.ui.graphics.Color = OnSurface) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    ) {
        Text(
            text,
            fontSize = 15.sp,
            color = textColor,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
