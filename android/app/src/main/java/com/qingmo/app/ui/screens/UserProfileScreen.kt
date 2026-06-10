package com.qingmo.app.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.qingmo.app.data.auth.TokenManager
import com.qingmo.app.data.api.RetrofitClient
import com.qingmo.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Base64

@Composable
fun UserProfileScreen(
    onLogout: () -> Unit,
    onBack: () -> Unit,
    onDramaClick: (Int) -> Unit = {},
    onEpisodeClick: (dramaId: Int, episodeId: Int) -> Unit = { _, _ -> },
) {
    val nickname = TokenManager.getNickname() ?: "未登录"
    val username = TokenManager.getUsername() ?: ""
    val userId = TokenManager.getUserId()
    val uid = if (userId > 0) userId.toString() else ""
    var editingName by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf(nickname) }
    var displayName by remember { mutableStateOf(nickname) }
    var avatarUrl by remember { mutableStateOf(TokenManager.getAvatar() ?: "") }
    var avatarBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    LaunchedEffect(avatarUrl) {
        if (avatarUrl.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                try {
                    val base64Part = avatarUrl.removePrefix("data:image/jpeg;base64,")
                    val bytes = java.util.Base64.getDecoder().decode(base64Part)
                    avatarBitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } catch (_: Exception) {}
            }
        } else {
            avatarBitmap = null
        }
    }

    var pendingRawUri by remember { mutableStateOf<Uri?>(null) }
    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { pendingRawUri = it }
    }
    val cropLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.extras?.get("data")?.let { croppedBitmap ->
                scope.launch(Dispatchers.IO) {
                    try {
                        val bitmap = croppedBitmap as Bitmap
                        val bos = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, bos)
                        val base64 = Base64.getEncoder().encodeToString(bos.toByteArray())
                        val dataUri = "data:image/jpeg;base64,$base64"
                        RetrofitClient.api.updateMe(mapOf("avatar" to dataUri))
                        withContext(Dispatchers.Main) {
                            avatarUrl = dataUri
                            TokenManager.saveAvatar(dataUri)
                            android.widget.Toast.makeText(ctx, "头像上传成功", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(ctx, "上传失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
        pendingRawUri = null
    }
    LaunchedEffect(pendingRawUri) {
        pendingRawUri?.let { uri ->
            val cropIntent = Intent("com.android.camera.action.CROP")
            cropIntent.setDataAndType(uri, "image/*")
            cropIntent.putExtra("crop", "true")
            cropIntent.putExtra("aspectX", 1)
            cropIntent.putExtra("aspectY", 1)
            cropIntent.putExtra("outputX", 256)
            cropIntent.putExtra("outputY", 256)
            cropIntent.putExtra("return-data", true)
            cropIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            cropLauncher.launch(Intent.createChooser(cropIntent, "裁剪头像"))
        }
    }

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

    // 子页面
    if (currentPage != "main") {
        Column(Modifier.fillMaxSize().background(Background).windowInsetsPadding(WindowInsets.statusBars)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { currentPage = "main" }) { Text("← 返回", fontSize = 16.sp, color = Primary) }
                Spacer(Modifier.width(8.dp))
                Text(when (currentPage) { "history" -> "观看历史"; "likes" -> "点赞记录"; "favorites" -> "我的收藏"; else -> "" }, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = OnSurface)
            }
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(Border.copy(alpha = 0.2f)))

            if (currentPage == "likes") {
                if (likeList.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("暂无记录", color = OnSurfaceMuted, fontSize = 14.sp) }
                } else {
                    LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(likeList.size) { idx ->
                            val r = likeList[idx]
                            val dramaId = (r["drama_id"] as? Number)?.toInt() ?: 0
                            val epId = (r["episode_id"] as? Number)?.toInt() ?: 0
                            val title = r["drama_title"] as? String ?: ""
                            val videoUrl = r["video_url"] as? String ?: ""
                            val fallbackCover = r["cover_url"] as? String ?: ""
                            val epNum = r["episode_num"] ?: ""
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { if (dramaId > 0 && epId > 0) onEpisodeClick(dramaId, epId) },
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            ) {
                                Column {
                                    Box(Modifier.fillMaxWidth().aspectRatio(0.75f).clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)).background(Color(0xFF1A535C).copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                                        val resolvedVideo = RetrofitClient.resolveMediaUrl(videoUrl)
                                        if (videoUrl.isNotEmpty()) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(ctx)
                                                    .data(resolvedVideo)
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else if (fallbackCover.isNotEmpty()) {
                                            AsyncImage(model = ImageRequest.Builder(ctx).data(RetrofitClient.resolveMediaUrl(fallbackCover)).crossfade(true).build(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                        } else {
                                            Text(title.take(3), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Primary.copy(alpha = 0.4f))
                                        }
                                    }
                                    Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                                        Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF333333), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("第${epNum}集", fontSize = 10.sp, color = OnSurfaceMuted, modifier = Modifier.padding(top = 2.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // 观看历史 / 收藏：剧集卡片（一部剧一条）
                val list = if (currentPage == "history") watchHistory else favList
                if (list.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("暂无记录", color = OnSurfaceMuted, fontSize = 14.sp) }
                } else {
                    LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(list.size) { idx ->
                            val r = list[idx]
                            val dramaId = (r["drama_id"] as? Number)?.toInt() ?: 0
                            val lastEpId = (r["last_ep_id"] as? Number)?.toInt()
                            val title = r["drama_title"] as? String ?: ""
                            val cover = r["cover_url"] as? String ?: ""
                            val epNum = r["last_ep_num"] ?: r["episode_num"] ?: ""
                            val total = r["total_episodes"] as? Number ?: 0
                            val progress = (r["progress"] as? Number)?.toLong() ?: 0L

                            Card(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    if (dramaId > 0) {
                                        if (lastEpId != null && lastEpId > 0) onEpisodeClick(dramaId, lastEpId)
                                        else onDramaClick(dramaId)
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            ) {
                                Column {
                                    Box(Modifier.fillMaxWidth().aspectRatio(0.75f).clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)).background(Color(0xFF1A535C).copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                                        if (cover.isNotEmpty()) {
                                            AsyncImage(model = ImageRequest.Builder(ctx).data(RetrofitClient.resolveMediaUrl(cover)).crossfade(true).build(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                        } else {
                                            Text(title.take(3), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Primary.copy(alpha = 0.4f))
                                        }
                                    }
                                    Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                                        Text(title, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF333333), maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 14.sp)
                                        val tc = r["total_episodes"] as? Number
                                        if (tc != null && tc.toInt() > 0) {
                                            if (currentPage == "history" && epNum != null) {
                                                Text("第${epNum}集 / 共${tc}集", fontSize = 10.sp, color = OnSurfaceMuted, modifier = Modifier.padding(top = 2.dp))
                                            } else {
                                                Text("共${tc}集", fontSize = 10.sp, color = OnSurfaceMuted, modifier = Modifier.padding(top = 2.dp))
                                            }
                                        }
                                        if (currentPage == "history" && progress > 0) {
                                            LinearProgressIndicator(progress = (progress / 1000f / 60f / 100f).coerceIn(0f, 0.99f), modifier = Modifier.fillMaxWidth().height(3.dp).padding(top = 4.dp), color = Primary, trackColor = Primary.copy(alpha = 0.1f))
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
                Box(Modifier.size(72.dp).clip(CircleShape).background(Primary.copy(alpha = 0.15f)).clickable {
                    pickImageLauncher.launch("image/*")
                }, contentAlignment = Alignment.Center) {
                    avatarBitmap?.let { bmp ->
                        AsyncImage(
                            model = ImageRequest.Builder(ctx).data(bmp).crossfade(true).build(),
                            contentDescription = "头像",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } ?: run {
                        Text(displayName.take(1), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Primary)
                    }
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
                            scope.launch(Dispatchers.IO) { try { RetrofitClient.api.updateProfileRaw(mapOf("user_id" to uid, "nickname" to newName)) } catch (_: Exception) {} }
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
                ProfileMenuItem("❤️ 点赞记录", onClick = { currentPage = "likes" })
                ProfileMenuItem("⭐ 我的收藏", onClick = { currentPage = "favorites" })
                HorizontalDivider(color = Border.copy(alpha = 0.2f), modifier = Modifier.padding(horizontal = 16.dp))
                ProfileMenuItem("🚪 退出登录", onClick = { TokenManager.clear(); onLogout() }, textColor = OnSurfaceMuted)
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
private fun ProfileMenuItem(text: String, onClick: () -> Unit, textColor: Color = OnSurface) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Text(text, fontSize = 15.sp, color = textColor, modifier = Modifier.fillMaxWidth())
    }
}
