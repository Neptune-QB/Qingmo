package com.qingmo.app.data.model

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName

@Immutable
data class DramaBrief(
    val id: Int,
    val title: String,
    val coverUrl: String,
    val tags: List<String>? = null,
    @SerializedName("total_episodes") val episodeCount: Int = 0,
)

@Immutable
data class EpisodeBrief(
    val episodeId: Long,
    val episodeNum: Int,
    val title: String? = null,
    val duration: Int = 0,
    val thumbnailUrl: String? = null,
)

@Immutable
data class DramaDetail(
    val id: Int,
    val title: String,
    @SerializedName("author") val author: String? = null,
    val description: String? = null,
    val coverUrl: String,
    val tags: List<String>? = null,
    val episodes: List<EpisodeBrief>,
    @SerializedName("fav_count") val favCount: Int = 0,
)

@Immutable
data class DramaHighlight(
    val id: Int = 0,
    @SerializedName("drama_id") val dramaId: Int = 0,
    @SerializedName("episode_id") val episodeId: Long = 0,
    @SerializedName("highlight_type") val highlightType: String = "",
    @SerializedName("start_time_ms") val startTimeMs: Int = 0,
    @SerializedName("end_time_ms") val endTimeMs: Int = 0,
    @SerializedName("hint_offset_ms") val hintOffsetMs: Int = 2000,
    val title: String = "",
    val description: String? = null,
    @SerializedName("interaction_type") val interactionType: String = "",
    @SerializedName("interaction_config") val interactionConfig: Map<String, @JvmSuppressWildcards Any> = emptyMap(),
    @SerializedName("xiaomo_gif_code") val xiaomoGifCode: String = "",
    val priority: Int = 0,
    val status: String = "enabled",
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("bubble_text") val bubbleText: String = "",
) {
    /** 从 interaction_config 提取情绪提示列表 */
    val emotionHints: List<String>
        get() {
            val list = interactionConfig["emotions"] ?: interactionConfig["emotion_hints"]
            return when (list) {
                is List<*> -> list.mapNotNull { it as? String }
                else -> emptyList()
            }
        }

    /** 从 interaction_config 提取选项列表 */
    val options: List<String>
        get() {
            val list = interactionConfig["options"]
            return when (list) {
                is List<*> -> list.mapNotNull { it as? String }
                else -> emptyList()
            }
        }
}

@Immutable
data class PlaybackInfo(
    val episodeId: Long,
    val videoUrl: String,
    val duration: Int = 0,
    val highlights: List<DramaHighlight> = emptyList(),
)

@Immutable
data class ProgressReport(
    @SerializedName("episode_id") val episodeId: Long,
    val progress: Int,
    @SerializedName("user_id") val userId: String = "0",
)
