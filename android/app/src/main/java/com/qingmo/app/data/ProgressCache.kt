package com.qingmo.app.data

object ProgressCache {
    private val cache = mutableMapOf<Long, Long>()
    private val watched = mutableSetOf<Long>()

    fun get(episodeId: Long): Long = cache[episodeId] ?: 0L

    fun save(
        episodeId: Long,
        position: Long,
    ) {
        cache[episodeId] = position
    }

    fun isWatched(episodeId: Long): Boolean = episodeId in watched

    fun markWatched(episodeId: Long) {
        watched.add(episodeId)
    }
}
