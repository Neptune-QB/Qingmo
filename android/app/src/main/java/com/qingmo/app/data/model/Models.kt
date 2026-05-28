package com.qingmo.app.data.model

import com.google.gson.annotations.SerializedName

data class DramaBrief(
    val id: Int,
    val title: String,
    val coverUrl: String,
    val tags: List<String>? = null,
    @SerializedName("totalEpisodes") val episodeCount: Int = 0,
)

data class EpisodeBrief(
    val episodeId: Long,
    val episodeNum: Int,
    val title: String? = null,
    val duration: Int = 0,
    val thumbnailUrl: String? = null,
)

data class DramaDetail(
    val id: Int,
    val title: String,
    @SerializedName("author") val author: String? = null,
    val description: String? = null,
    val coverUrl: String,
    val tags: List<String>? = null,
    val episodes: List<EpisodeBrief>,
)

data class HighlightItem(
    val id: Int,
    val episodeId: Long,
    val time: Int,
    val type: String,
    val title: String,
    @SerializedName("widget_type") val widgetType: String = "emoji",
    val options: List<String>? = null,
)

data class PlaybackInfo(
    val episodeId: Long,
    val videoUrl: String,
    val duration: Int = 0,
    val highlights: List<HighlightItem> = emptyList(),
)

data class ProgressReport(
    val episodeId: Long,
    val progress: Int,
)
