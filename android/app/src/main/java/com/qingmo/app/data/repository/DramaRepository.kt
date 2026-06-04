package com.qingmo.app.data.repository

import com.qingmo.app.data.api.RetrofitClient
import com.qingmo.app.data.model.DramaBrief
import com.qingmo.app.data.model.DramaDetail
import com.qingmo.app.data.model.PlaybackInfo
import com.qingmo.app.data.user.UserProfile
import com.qingmo.app.data.user.UserProfileUpdate
import com.qingmo.app.data.user.WatchHistoryItem
import kotlinx.coroutines.CancellationException

/**
 * 安全调用 API，不吞没 CancellationException 以避免协程取消失效
 */
private suspend fun <T> safeApiCall(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

class DramaRepository {
    private val api = RetrofitClient.api

    suspend fun getDramas(): Result<List<DramaBrief>> =
        safeApiCall { api.getDramas() }

    suspend fun getDramaDetail(dramaId: Int): Result<DramaDetail> =
        safeApiCall { api.getDramaDetail(dramaId) }

    suspend fun getPlaybackInfo(episodeId: Long): Result<PlaybackInfo> =
        safeApiCall { api.getPlaybackInfo(episodeId) }

    suspend fun reportProgress(
        episodeId: Long,
        progress: Int,
    ): Result<Unit> =
        safeApiCall { api.reportProgress(episodeId, progress) }

    suspend fun reportInteraction(data: Map<String, Any>): Result<Unit> =
        safeApiCall { api.reportInteraction(data) }

    suspend fun getUserProfile(userId: String): Result<UserProfile> =
        safeApiCall { api.getUserProfile(userId) }

    suspend fun updateUserProfile(update: UserProfileUpdate): Result<Unit> =
        safeApiCall { api.updateUserProfile(update) }

    suspend fun getWatchHistory(userId: String, limit: Int = 30): Result<List<WatchHistoryItem>> =
        safeApiCall { api.getWatchHistory(userId, limit) }
}
