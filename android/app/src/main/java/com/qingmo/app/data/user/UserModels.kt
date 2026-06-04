package com.qingmo.app.data.user

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName

/**
 * 观看历史条目
 */
@Immutable
data class WatchHistoryItem(
    @SerializedName("episode_id") val episodeId: Int,
    @SerializedName("drama_id") val dramaId: Int,
    @SerializedName("drama_title") val dramaTitle: String = "",
    @SerializedName("episode_num") val episodeNum: Int = 0,
    val progress: Int = 0,
    val watched: Int = 0,
)

/**
 * 用户画像
 */
@Immutable
data class UserProfile(
    @SerializedName("user_id") val userId: String,
    @SerializedName("watch_history") val watchHistory: List<WatchHistoryItem> = emptyList(),
    @SerializedName("interaction_stats") val interactionStats: Map<String, Int> = emptyMap(),
    @SerializedName("favorite_dramas") val favoriteDramas: List<Int> = emptyList(),
    val preferences: Map<String, Float> = emptyMap(),
)

/**
 * 画像更新请求
 */
@Immutable
data class UserProfileUpdate(
    @SerializedName("user_id") val userId: String,
    @SerializedName("favorite_dramas") val favoriteDramas: List<Int>? = null,
    val preferences: Map<String, Float>? = null,
)
