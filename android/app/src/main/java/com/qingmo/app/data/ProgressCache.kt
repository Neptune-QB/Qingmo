package com.qingmo.app.data

import java.util.concurrent.ConcurrentHashMap

object ProgressCache {
    private val cache = ConcurrentHashMap<Long, Long>()
    private val watched = ConcurrentHashMap.newKeySet<Long>()

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
