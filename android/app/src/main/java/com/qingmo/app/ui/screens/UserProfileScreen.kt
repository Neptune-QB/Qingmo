package com.qingmo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.qingmo.app.data.api.RetrofitClient
import com.qingmo.app.data.auth.TokenManager
import com.qingmo.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun UserProfileScreen(
    onLogout: () -> Unit,
    onBack: () -> Unit,
    onDramaClick: (Int) -> Unit = {},
) {
    val nickname = TokenManager.getNickname() ?: "未登录"
    val username = TokenManager.getUsername() ?: ""
    val userId = TokenManager.getUserId()
    val uid = if (userId > 0) userId.toString() else ""
    var editingName by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf(nickname) }
    var displayName by remember { mutableStateOf(nickname) }
    val scope = rememberCoroutineScope()

    var currentPage by remember { mutableStateOf("main") }
    var watchHistory by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var likeList by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var favList by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }

    fun loadData() {
        scope.launch(Dispatchers.IO) {
            try {
                val h = RetrofitClient.api.getWatchHistory(uid)
                val f = RetrofitClient.api.getFavorites(uid)
                var l = emptyList<Map<String, Any>>()
                try { l = RetrofitClient.api.getUserLikes(uid) } catch (_: Exception) {}
                withContext(Dispatchers.Main) {
                    watchHistory = h.map { it as? Map<String, Any> ?: emptyMap() }
                    likeList = l; favList = f
                }
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(Unit) { loadData() }

    // 子页面：观看历史 / 收藏 / 点赞
    if (currentPage != "main") {
        Column(Modifier.fillMaxSize().background(Background).windowInsetsPadding(WindowInsets.statusBars)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { currentPage = "main" }) { Text("← 返回", fontSize = 16.sp, color = Primary) }
                Spacer(Modifier.width(8.dp))
                Text(when (currentPage) { "history" -> "观看历史" else -> "我的收藏" }, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = OnSurface)
            }
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(Border.copy(alpha = 0.2f)))
            val list = when (currentPage) { "history" -> watchHistory else -> favList }
            if (list.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("暂无记录", color = OnSurfaceMuted, fontSize = 14.sp) }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    items(list.size) { idx ->
                        val r = list[idx]
                        val dramaId = (r["drama_id"] as? Number)?.toInt() ?: 0
                        val title = r["drama_title"] as? String ?: ""
                        val cover = r["cover_url"] as? String ?: ""
                        val epNum = r["episode_num"] ?: ""
                        val total = r["total_episodes"] as? Number ?: 0
                        val progress = (r["progress"] as? Number)?.toLong() ?: 0L

                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable {
                                if (dramaId > 0) onDramaClick(dramaId)
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(72.dp, 96.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF1A535C).copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                                    if (cover.isNotEmpty()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current).data(cover).crossfade(true).build(),
                                            contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                                        )
                                    } else {
                                        Text(title.take(2), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Primary.copy(alpha = 0.5f))
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF333333), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(4.dp))
                                    Text("第${epNum}集 / 共${total}集", fontSize = 12.sp, color = OnSurfaceMuted)
                                    if (currentPage == "history" && progress > 0) {
                                        Spacer(Modifier.height(6.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            LinearProgressIndicator(progress = (progress.toFloat() / 1000f / 60f).coerceIn(0f, 1f), modifier = Modifier.weight(1f).height(4.dp), color = Primary, trackColor = Primary.copy(alpha = 0.1f))
                                            Spacer(Modifier.width(8.dp))
                                            Text("${formatSec(progress / 1000)}", fontSize = 11.sp, color = OnSurfaceMuted)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return
    }

    // 主页
    Column(Modifier.fillMaxSize().background(Background).verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("我的", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = OnSurface)
            TextButton(onClick = onBack) { Text("完成", color = Primary) }
        }

        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(16.dp)).background(SurfaceVariant.copy(alpha = 0.3f)).padding(20.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.size(72.dp).clip(CircleShape).background(Primary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Text(displayName.take(1), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Primary)
                }
                Spacer(Modifier.height(12.dp))
                if (editingName) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = editText, onValueChange = { editText = it },
                            modifier = Modifier.width(140.dp).background(Color.White, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(color = OnSurface, fontSize = 16.sp), singleLine = true,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("✓", fontSize = 20.sp, color = Primary, modifier = Modifier.clickable {
                            editingName = false; val newName = editText.trim().ifEmpty { nickname }
                            scope.launch(Dispatchers.IO) { try { RetrofitClient.api.updateMe(mapOf("nickname" to newName, "avatar" to "")) } catch (_: Exception) {} }
                            displayName = newName
                        })
                        Spacer(Modifier.width(8.dp))
                        Text("✕", fontSize = 18.sp, color = OnSurfaceMuted, modifier = Modifier.clickable { editingName = false; editText = displayName })
                    }
                } else {
                    Text(displayName, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = OnSurface, modifier = Modifier.clickable { editingName = true; editText = displayName })
                }
                Text("@$username", fontSize = 13.sp, color = OnSurfaceMuted, modifier = Modifier.padding(top = 4.dp))
            }
        }

        Spacer(Modifier.height(16.dp))

        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(16.dp)).background(SurfaceVariant.copy(alpha = 0.3f)).padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatItem("观看历史", "${watchHistory.size}")
                StatItem("点赞", "${likeList.size}")
                StatItem("收藏", "${favList.size}")
            }
        }

        Spacer(Modifier.height(16.dp))

        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(16.dp)).background(SurfaceVariant.copy(alpha = 0.3f)).padding(4.dp)) {
            Column {
                ProfileMenuItem("📺 观看历史", onClick = { currentPage = "history" })
                ProfileMenuItem("⭐ 我的收藏", onClick = { currentPage = "favorites" })
                HorizontalDivider(color = Border.copy(alpha = 0.2f), modifier = Modifier.padding(horizontal = 16.dp))
                ProfileMenuItem("🚪 退出登录", onClick = { TokenManager.clear(); onLogout() }, textColor = OnSurfaceMuted)
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

private fun formatSec(totalSec: Long): String {
    val m = totalSec / 60; val s = totalSec % 60
    return "${m}:${s.toString().padStart(2, '0')}"
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Primary)
        Text(label, fontSize = 12.sp, color = OnSurfaceMuted)
    }
}

@Composable
private fun ProfileMenuItem(text: String, onClick: () -> Unit, textColor: Color = OnSurface) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Text(text, fontSize = 15.sp, color = textColor, modifier = Modifier.fillMaxWidth())
    }
}
