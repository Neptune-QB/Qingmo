package com.qingmo.app.data.api

import com.qingmo.app.data.model.DramaBrief
import com.qingmo.app.data.model.DramaDetail
import com.qingmo.app.data.model.PlaybackInfo
import com.qingmo.app.data.user.UserProfile
import com.qingmo.app.data.user.UserProfileUpdate
import com.qingmo.app.data.user.WatchHistoryItem
import retrofit2.http.Body
import retrofit2.http.DELETE
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

    @GET("api/v1/user/favorites")
    suspend fun getFavorites(
        @Query("user_id") userId: String = "",
    ): List<Map<String, @JvmSuppressWildcards Any>>

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

    @GET("api/v1/episodes/{episodeId}/comments/ai-reply-status")
    suspend fun getAiReplyStatus(
        @Path("episodeId") episodeId: Long,
        @Query("parent_comment_ids") parentCommentIds: String,
    ): Map<String, @JvmSuppressWildcards Any>

    // 小墨会话
    @POST("api/v1/agent/sessions")
    suspend fun createSession(@Body body: Map<String, @JvmSuppressWildcards Any>): Map<String, @JvmSuppressWildcards Any>

    @GET("api/v1/agent/sessions")
    suspend fun listSessions(@Query("user_id") userId: String = ""): List<Map<String, @JvmSuppressWildcards Any>>

    @POST("api/v1/agent/sessions/{id}/title")
    suspend fun updateSessionTitle(
        @Path("id") sessionId: Int,
        @Body body: Map<String, @JvmSuppressWildcards Any>,
    ): Map<String, @JvmSuppressWildcards Any>

    @DELETE("api/v1/agent/sessions/{id}")
    suspend fun deleteSession(@Path("id") sessionId: Int): Map<String, @JvmSuppressWildcards Any>

    @GET("api/v1/agent/sessions/{id}/messages")
    suspend fun getSessionMessages(@Path("id") sessionId: Int): List<Map<String, @JvmSuppressWildcards Any>>

    @POST("api/v1/agent/sessions/messages/append")
    suspend fun appendMessage(
        @Body body: Map<String, @JvmSuppressWildcards Any>,
    ): Map<String, @JvmSuppressWildcards Any>

    // 剧情投票
    @GET("api/v1/highlights/{highlightId}/vote")
    suspend fun getHighlightVote(
        @Path("highlightId") highlightId: Int,
        @Query("user_id") userId: String = "",
    ): Map<String, @JvmSuppressWildcards Any>

    @POST("api/v1/highlights/{highlightId}/vote")
    suspend fun castHighlightVote(
        @Path("highlightId") highlightId: Int,
        @Body body: Map<String, @JvmSuppressWildcards Any>,
    ): Map<String, @JvmSuppressWildcards Any>

    // AI剧情问答
    @GET("api/v1/highlights/{highlightId}/quiz")
    suspend fun getHighlightQuiz(
        @Path("highlightId") highlightId: Int,
    ): Map<String, @JvmSuppressWildcards Any>

    // 角色AI对话
    @GET("api/v1/characters")
    suspend fun listCharacters(
        @Query("drama_id") dramaId: Int,
    ): List<Map<String, @JvmSuppressWildcards Any>>

    @POST("api/v1/characters/{charId}/chat")
    suspend fun characterChat(
        @Path("charId") charId: Int,
        @Body body: Map<String, @JvmSuppressWildcards Any>,
    ): Map<String, @JvmSuppressWildcards Any>

    // 追剧笔记
    @POST("api/v1/episodes/{episodeId}/notes")
    suspend fun createNote(
        @Path("episodeId") episodeId: Long,
        @Body body: Map<String, @JvmSuppressWildcards Any>,
    ): Map<String, @JvmSuppressWildcards Any>

    @GET("api/v1/episodes/{episodeId}/notes")
    suspend fun getEpisodeNotes(
        @Path("episodeId") episodeId: Long,
        @Query("user_id") userId: String,
    ): List<Map<String, @JvmSuppressWildcards Any>>

    @GET("api/v1/user/notes")
    suspend fun getAllNotes(
        @Query("user_id") userId: String,
        @Query("limit") limit: Int = 50,
    ): List<Map<String, @JvmSuppressWildcards Any>>

    @DELETE("api/v1/notes/{noteId}")
    suspend fun deleteNote(
        @Path("noteId") noteId: Int,
    ): Map<String, @JvmSuppressWildcards Any>

    // 剧情分支投票
    @GET("api/v1/dramas/{dramaId}/branch-vote")
    suspend fun getBranchVote(
        @Path("dramaId") dramaId: Int,
        @Query("user_id") userId: String = "",
    ): Map<String, @JvmSuppressWildcards Any>

    @POST("api/v1/dramas/{dramaId}/branch-vote")
    suspend fun castBranchVote(
        @Path("dramaId") dramaId: Int,
        @Body body: Map<String, @JvmSuppressWildcards Any>,
    ): Map<String, @JvmSuppressWildcards Any>

    // 高光剧情气泡 (LLM生成)
    @GET("api/v1/highlights/{highlightId}/bubble")
    suspend fun getHighlightBubble(
        @Path("highlightId") highlightId: Int,
    ): Map<String, @JvmSuppressWildcards Any>
}
