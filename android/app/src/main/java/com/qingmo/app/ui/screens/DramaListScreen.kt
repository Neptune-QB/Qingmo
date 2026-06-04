package com.qingmo.app.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.qingmo.app.data.api.RetrofitClient
import com.qingmo.app.data.model.DramaBrief
import com.qingmo.app.data.repository.DramaRepository
import com.qingmo.app.ui.theme.Background
import com.qingmo.app.ui.theme.GradientCyanDark
import com.qingmo.app.ui.theme.OnSurface
import com.qingmo.app.ui.theme.OnSurfaceVariant
import com.qingmo.app.ui.theme.Primary
import com.qingmo.app.ui.theme.SurfaceElevated
import com.qingmo.app.ui.theme.SurfaceVariant
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("ktlint:standard:function-naming")
@Composable
fun DramaListScreen(onDramaClick: (Int) -> Unit, onProfileClick: () -> Unit = {}) {
    val repository = remember { DramaRepository() }
    var dramas by remember { mutableStateOf<List<DramaBrief>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun loadDramas() {
        scope.launch {
            isLoading = true
            error = null
            try {
                val result = repository.getDramas()
                result
                    .onSuccess {
                        dramas = it
                        if (it.isEmpty()) {
                            error = "后端返回空列表"
                        }
                    }
                    .onFailure {
                        error = "错误: ${it.message ?: "未知异常"}"
                    }
            } catch (e: Throwable) {
                error = "外层捕获异常: ${e.message ?: e.javaClass.simpleName}"
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadDramas() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "青墨",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
                    IconButton(onClick = onProfileClick) {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = "我的",
                            tint = OnSurface,
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                    ),
            )
        },
        containerColor = Background,
    ) { padding ->
        when {
            isLoading -> LoadingGrid(Modifier.padding(padding))
            error != null ->
                ErrorState(
                    message = error!!,
                    onRetry = { loadDramas() },
                    modifier = Modifier.padding(padding),
                )
            dramas.isEmpty() -> EmptyState(Modifier.padding(padding))
            else ->
                DramaGrid(
                    dramas = dramas,
                    onDramaClick = onDramaClick,
                    modifier = Modifier.padding(padding),
                )
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun LoadingGrid(modifier: Modifier = Modifier) {
    val alpha = 0.4f

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(6) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.75f)
                            .background(
                                Brush.verticalGradient(
                                    colors =
                                        listOf(
                                            SurfaceVariant.copy(alpha = alpha),
                                            Background.copy(alpha = alpha + 0.1f),
                                        ),
                                ),
                            ),
                    )
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Box(
                            Modifier
                                .fillMaxWidth(0.75f)
                                .height(14.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(SurfaceVariant.copy(alpha = alpha)),
                        )
                        Spacer(Modifier.height(8.dp))
                        Box(
                            Modifier
                                .fillMaxWidth(0.45f)
                                .height(10.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(SurfaceVariant.copy(alpha = alpha * 0.7f)),
                        )
                    }
                }
            }
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "加载失败",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onRetry,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = Primary,
                        contentColor = Color.White,
                    ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("重试")
            }
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "暂无短剧内容",
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceVariant,
        )
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun DramaGrid(
    dramas: List<DramaBrief>,
    onDramaClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        itemsIndexed(dramas, key = { _, d -> d.id }) { _, drama ->
            DramaCard(
                drama = drama,
                onClick = { onDramaClick(drama.id) },
            )
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun DramaCard(
    drama: DramaBrief,
    onClick: () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        label = "card_scale",
    )

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .scale(scale)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            // --- 封面图 + 渐变叠加 ---
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.75f),
            ) {
                AsyncImage(
                    model = RetrofitClient.resolveMediaUrl(drama.coverUrl),
                    contentDescription = drama.title,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop,
                )
                // 底部渐变叠加，让文字更清晰
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .align(Alignment.BottomCenter)
                        .background(GradientCyanDark),
                )
                // 集数标签
                if (drama.episodeCount > 0) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.45f),
                    ) {
                        Text(
                            "${drama.episodeCount}集",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
            // --- 标题 + 标签 ---
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(
                    drama.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!drama.tags.isNullOrEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        drama.tags.take(2).forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Primary.copy(alpha = 0.08f),
                            ) {
                                Text(
                                    tag,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Primary,
                                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
