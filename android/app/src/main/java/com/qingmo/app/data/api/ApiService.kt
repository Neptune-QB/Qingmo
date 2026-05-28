package com.qingmo.app.data.api

import com.qingmo.app.data.model.DramaBrief
import com.qingmo.app.data.model.DramaDetail
import com.qingmo.app.data.model.PlaybackInfo
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
}
