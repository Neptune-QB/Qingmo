package com.qingmo.app.data.auth

import com.google.gson.annotations.SerializedName

/**
 * 注册请求
 */
data class RegisterRequest(
    val username: String,
    val password: String,
    val nickname: String = "",
    @SerializedName("device_id") val deviceId: String = "",
)

/**
 * 登录请求
 */
data class LoginRequest(
    val username: String,
    val password: String,
    @SerializedName("device_id") val deviceId: String = "",
)

/**
 * 鉴权响应（登录/注册/刷新 共用）
 */
data class AuthResponse(
    val ok: Boolean,
    @SerializedName("user_id") val userId: Int = 0,
    val username: String = "",
    val nickname: String = "",
    val token: String = "",
    val error: String = "",
)

/**
 * 用户完整信息
 */
data class UserInfo(
    val id: Int,
    val username: String,
    val nickname: String,
    val avatar: String = "",
    @SerializedName("device_ids") val deviceIds: List<String> = emptyList(),
    @SerializedName("created_at") val createdAt: String = "",
    val stats: UserStats = UserStats(),
)

data class UserStats(
    @SerializedName("watched_episodes") val watchedEpisodes: Int = 0,
    @SerializedName("interaction_total") val interactionTotal: Int = 0,
    @SerializedName("by_module") val byModule: Map<String, Int> = emptyMap(),
)

/**
 * 资料更新请求
 */
data class ProfileUpdateRequest(
    val nickname: String = "",
    val avatar: String = "",
)
