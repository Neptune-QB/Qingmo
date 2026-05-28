package com.qingmo.app.data.repository

import com.qingmo.app.data.api.RetrofitClient
import com.qingmo.app.data.model.DramaBrief
import com.qingmo.app.data.model.DramaDetail
import com.qingmo.app.data.model.PlaybackInfo

class DramaRepository {
    private val api = RetrofitClient.api

    suspend fun getDramas(): Result<List<DramaBrief>> =
        runCatching {
            api.getDramas()
        }

    suspend fun getDramaDetail(dramaId: Int): Result<DramaDetail> =
        runCatching {
            api.getDramaDetail(dramaId)
        }

    suspend fun getPlaybackInfo(episodeId: Long): Result<PlaybackInfo> =
        runCatching {
            api.getPlaybackInfo(episodeId)
        }

    suspend fun reportProgress(
        episodeId: Long,
        progress: Int,
    ): Result<Unit> =
        runCatching {
            api.reportProgress(episodeId, progress)
        }
}
