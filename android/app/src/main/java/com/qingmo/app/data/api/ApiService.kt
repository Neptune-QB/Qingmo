package com.qingmo.app.data.api

import com.qingmo.app.data.model.DramaBrief
import com.qingmo.app.data.model.DramaDetail
import com.qingmo.app.data.model.PlaybackInfo
import com.qingmo.app.data.user.UserProfile
import com.qingmo.app.data.user.UserProfileUpdate
import com.qingmo.app.data.user.WatchHistoryItem
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @GET("api/v1/dramas")
    suspend fun getDramas(): List<DramaBrief>

    @GET("api/v1/dramas/{id}")
    suspend fun getDramaDetail(
        @Path("id") dramaId: Int,
    ): DramaDetail

    @GET("api/v1/playback/{episodeId}")
    suspend fun getPlaybackInfo(
        @Path("episodeId") episodeId: Long,
    ): PlaybackInfo

    @POST("api/v1/progress")
    suspend fun reportProgress(
        @Query("episode_id") episodeId: Long,
        @Query("progress") progress: Int,
    )

    @POST("api/v1/interactions")
    suspend fun reportInteraction(
        @Body body: Map<String, @JvmSuppressWildcards Any>,
    )

    @GET("api/v1/user/profile")
    suspend fun getUserProfile(
        @Query("user_id") userId: String,
    ): UserProfile

    @POST("api/v1/user/profile")
    suspend fun updateUserProfile(
        @Body body: UserProfileUpdate,
    )

    @GET("api/v1/user/watch-history")
    suspend fun getWatchHistory(
        @Query("user_id") userId: String,
        @Query("limit") limit: Int = 30,
    ): List<WatchHistoryItem>

    @GET("api/v1/danmaku/{episodeId}")
    suspend fun getDanmaku(
        @Path("episodeId") episodeId: Long,
        @Query("limit") limit: Int = 2000,
        @Query("time_from") timeFrom: Float? = null,
        @Query("time_to") timeTo: Float? = null,
    ): List<Map<String, @JvmSuppressWildcards Any>>

    @POST("api/v1/danmaku")
    suspend fun postDanmaku(@Body body: Map<String, @JvmSuppressWildcards Any>): Map<String, @JvmSuppressWildcards Any>

    @POST("api/v1/episodes/{episodeId}/like")
    suspend fun toggleLike(
        @Path("episodeId") episodeId: Long,
        @Body body: Map<String, @JvmSuppressWildcards Any>,
    ): Map<String, @JvmSuppressWildcards Any>

    @GET("api/v1/episodes/{episodeId}/likes")
    suspend fun getLikes(
        @Path("episodeId") episodeId: Long,
        @Query("user_id") userId: String = "",
    ): Map<String, @JvmSuppressWildcards Any>

    @POST("api/v1/dramas/{dramaId}/favorite")
    suspend fun toggleFavorite(
        @Path("dramaId") dramaId: Int,
        @Body body: Map<String, @JvmSuppressWildcards Any>,
    ): Map<String, @JvmSuppressWildcards Any>

    @GET("api/v1/episodes/{episodeId}/counts")
    suspend fun getEpisodeCounts(
        @Path("episodeId") episodeId: Long,
        @Query("user_id") userId: String = "",
    ): Map<String, @JvmSuppressWildcards Any>

    @POST("api/v1/episodes/{episodeId}/comments")
    suspend fun postComment(
        @Path("episodeId") episodeId: Long,
        @Body body: Map<String, @JvmSuppressWildcards Any>,
    ): Map<String, @JvmSuppressWildcards Any>

    @GET("api/v1/episodes/{episodeId}/comments")
    suspend fun getComments(
        @Path("episodeId") episodeId: Long,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): List<Map<String, @JvmSuppressWildcards Any>>
}
