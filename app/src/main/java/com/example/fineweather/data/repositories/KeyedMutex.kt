package com.example.fineweather.data.repositories

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * One lazily created [Mutex] per key: the same key runs one at a time, different keys in parallel.
 * The map is never pruned; a mutex per place and request kind is small enough for an app session.
 */
class KeyedMutex {
    private val mutexes = ConcurrentHashMap<String, Mutex>()

    suspend fun <T> withLock(
        key: String,
        action: suspend () -> T,
    ): T = mutexes.getOrPut(key) { Mutex() }.withLock { action() }
}
