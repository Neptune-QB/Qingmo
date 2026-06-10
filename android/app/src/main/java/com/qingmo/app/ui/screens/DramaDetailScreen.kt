package com.qingmo.app.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.qingmo.app.data.ProgressCache
import com.qingmo.app.data.api.RetrofitClient
import com.qingmo.app.data.model.DramaDetail
import com.qingmo.app.data.model.EpisodeBrief
import com.qingmo.app.data.repository.DramaRepository
import com.qingmo.app.ui.theme.Background
import com.qingmo.app.ui.theme.Border
import com.qingmo.app.ui.theme.GraphiteTeal
import com.qingmo.app.ui.theme.OnBackground
import com.qingmo.app.ui.theme.OnSurface
import com.qingmo.app.ui.theme.OnSurfaceVariant
import com.qingmo.app.ui.theme.Primary
import com.qingmo.app.ui.theme.SurfaceElevated
import com.qingmo.app.ui.theme.SurfaceVariant
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("ktlint:standard:function-naming")
@Composable
fun DramaDetailScreen(
    dramaId: Int,
    onEpisodeClick: (Long) -> Unit,
    onBack: () -> Unit,
    currentEpisodeId: Long = -1L,
) {
    val repository = remember { DramaRepository() }
    var drama by remember { mutableStateOf<DramaDetail?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(dramaId) {
        isLoading = true
        error = null
        repository.getDramaDetail(dramaId)
            .onSuccess { drama = it }
            .onFailure { error = it.message ?: "\u52A0\u8F7D\u5931\u8D25" }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
                title = {
                    Text(
                        drama?.title ?: "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("\u2190 \u8FD4\u56DE", color = Primary)
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Background.copy(alpha = 0.92f),
                        titleContentColor = OnBackground,
                    ),
            )
        },
        containerColor = Background,
    ) { padding ->
        if (isLoading) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("\u52A0\u8F7D\u4E2D\u2026", color = OnSurfaceVariant)
            }
            return@Scaffold
        }

        if (error != null && drama == null) {
            Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(error!!, color = OnSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = {
                    isLoading = true
                    error = null
                    scope.launch {
                        repository.getDramaDetail(dramaId)
                            .onSuccess { drama = it }
                            .onFailure { error = it.message ?: "\u52A0\u8F7D\u5931\u8D25" }
                        isLoading = false
                    }
                }) {
                    Text("\u91CD\u8BD5", color = Primary)
                }
            }
            return@Scaffold
        }

        drama?.let { d ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .animateContentSize(),
            ) {
                // --- 封面区域：渐变融合背景 ---
                Box(Modifier.fillMaxWidth().height(300.dp)) {
                    AsyncImage(
                        model = RetrofitClient.resolveMediaUrl(d.coverUrl),
                        contentDescription = d.title,
                        modifier =
                            Modifier.fillMaxSize().clip(
                                RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                            ),
                        contentScale = ContentScale.Crop,
                    )
                    // 顶部渐变（与 TopAppBar 融合）
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(listOf(Background.copy(alpha = 0.6f), Color.Transparent)),
                            ),
                    )
                    // 底部渐变（与内容区融合）
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Background.copy(alpha = 0.85f), Background),
                                ),
                            ),
                    )
                    // 标题 + 集数叠加在封面底部
                    Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp, vertical = 16.dp)) {
                        Text(
                            d.title,
                            style = MaterialTheme.typography.headlineMedium,
                            color = OnBackground,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "共 ${d.episodes.size} 集",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariant,
                        )
                    }
                }

                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    // --- 标签行 ---
                    if (!d.tags.isNullOrEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 16.dp),
                        ) {
                            d.tags.take(4).forEach { tag ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Primary.copy(alpha = 0.1f),
                                ) {
                                    Text(
                                        tag,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Primary,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }
                    }

                    // --- 简介 ---
                    if (!d.description.isNullOrBlank()) {
                        var expanded by remember { mutableStateOf(false) }
                        var hasOverflow by remember { mutableStateOf(false) }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = SurfaceVariant.copy(alpha = 0.5f),
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text(
                                    d.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = OnSurface,
                                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
                                    onTextLayout = { hasOverflow = it.hasVisualOverflow },
                                )
                                if (hasOverflow || expanded) {
                                    TextButton(
                                        onClick = { expanded = !expanded },
                                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp),
                                    ) {
                                        Text(
                                            if (expanded) "收起" else "展开全部",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Primary,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // --- 选集 ---
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "选集",
                        style = MaterialTheme.typography.titleMedium,
                        color = OnBackground,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(12.dp))

                    val chunked = d.episodes.chunked(5)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        chunked.forEach { row ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                row.forEach { ep ->
                                    val watched = ProgressCache.isWatched(ep.episodeId)
                                    EpisodeItem(
                                        ep,
                                        isCurrent = ep.episodeId == currentEpisodeId,
                                        isWatched = watched,
                                        onClick = { onEpisodeClick(ep.episodeId) },
                                        modifier = Modifier.weight(1f).aspectRatio(1f),
                                    )
                                }
                                repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                    Spacer(Modifier.height(40.dp))
                }
            }
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun EpisodeItem(
    episode: EpisodeBrief,
    isCurrent: Boolean = false,
    isWatched: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val textColor =
        when {
            isCurrent -> Color.White
            isWatched -> OnSurfaceVariant.copy(alpha = 0.4f)
            else -> OnSurface
        }
    val bgColor =
        when {
            isCurrent -> GraphiteTeal
            isWatched -> SurfaceVariant.copy(alpha = 0.4f)
            else -> SurfaceElevated
        }
    val border =
        when {
            isCurrent -> BorderStroke(1.5.dp, GraphiteTeal)
            isWatched -> BorderStroke(0.5.dp, Border.copy(alpha = 0.3f))
            else -> BorderStroke(0.5.dp, Border)
        }
    val fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = border,
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                "${episode.episodeNum}",
                style = MaterialTheme.typography.labelLarge,
                color = textColor,
                fontWeight = fontWeight,
            )
        }
    }
}
