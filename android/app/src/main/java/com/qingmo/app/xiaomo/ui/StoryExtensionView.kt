package com.qingmo.app.xiaomo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingmo.app.data.chat.ChatService
import com.qingmo.app.ui.theme.Border
import com.qingmo.app.ui.theme.OnSurface
import com.qingmo.app.ui.theme.OnSurfaceMuted
import com.qingmo.app.ui.theme.Primary
import com.qingmo.app.ui.theme.SurfaceVariant
import com.qingmo.app.xiaomo.XiaoMoCore
import com.qingmo.app.xiaomo.XiaoMoEmotion
import com.qingmo.app.xiaomo.XiaoMoPose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AI 剧情续写组件
 *
 * 剧集播放完毕后展示续写入口，点击后调用 LLM 生成后续剧情
 */
@Composable
fun StoryExtensionView(
    dramaTitle: String,
    dramaDesc: String,
    latestEpisodes: List<String>,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "剧集完结！",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = OnSurface,
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = "想让小墨帮你续写后续剧情吗？",
            fontSize = 13.sp,
            color = OnSurfaceMuted,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(16.dp))

        // 续写按钮
        if (!isLoading && result == null && error == null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Primary)
                    .clickable {
                        isLoading = true
                        scope.launch {
                            try {
                                val story = withContext(Dispatchers.IO) {
                                    ChatService.getStoryExtension(
                                        dramaTitle = dramaTitle,
                                        dramaDesc = dramaDesc,
                                        latestEpisodes = latestEpisodes,
                                    )
                                }
                                result = story
                                XiaoMoCore.setEmotion(XiaoMoEmotion.Excited)
                                XiaoMoCore.setPose(XiaoMoPose.Playing)
                            } catch (e: Exception) {
                                error = "续写失败：${e.message}"
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                    .padding(horizontal = 24.dp, vertical = 10.dp),
            ) {
                Text(
                    text = "✨ AI 续写剧情",
                    fontSize = 15.sp,
                    color = androidx.compose.ui.graphics.Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // 加载中
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.padding(16.dp),
                color = Primary,
                strokeWidth = 2.dp,
            )
            Text(
                text = "小墨正在构思后续剧情...",
                fontSize = 12.sp,
                color = OnSurfaceMuted,
            )
        }

        // 续写结果
        AnimatedVisibility(
            visible = result != null,
            enter = fadeIn(),
        ) {
            result?.let { story ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceVariant.copy(alpha = 0.5f))
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = "📖 剧情续写",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Primary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = story,
                        fontSize = 13.sp,
                        color = OnSurface,
                        lineHeight = 20.sp,
                    )
                    // 反馈按钮
                    if (feedback == null) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Primary.copy(alpha = 0.1f))
                                    .clickable { feedback = "👍" }
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                            ) {
                                Text("👍 不错", fontSize = 12.sp, color = OnSurface)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Border.copy(alpha = 0.3f))
                                    .clickable { feedback = "👎" }
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                            ) {
                                Text("👎 一般", fontSize = 12.sp, color = OnSurface)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Primary)
                                    .clickable {
                                        result = null
                                        isLoading = true
                                        feedback = null
                                        scope.launch {
                                            try {
                                                val story = withContext(Dispatchers.IO) {
                                                    ChatService.getStoryExtension(dramaTitle, dramaDesc, latestEpisodes)
                                                }
                                                result = story
                                            } catch (e: Exception) {
                                                error = "续写失败：${e.message}"
                                            } finally {
                                                isLoading = false
                                            }
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    "🔄 再来一段",
                                    fontSize = 12.sp,
                                    color = androidx.compose.ui.graphics.Color.White,
                                )
                            }
                        }
                    } else {
                        Text(
                            text = if (feedback == "👍") "谢谢喜欢！小墨会继续努力的~ ❤️" else "收到反馈！小墨下次争取写得更好~ 💪",
                            fontSize = 12.sp,
                            color = OnSurfaceMuted,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }

        // 错误提示
        error?.let { err ->
            Text(
                text = err,
                fontSize = 12.sp,
                color = OnSurfaceMuted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
