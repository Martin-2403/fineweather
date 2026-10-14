package com.example.fineweather.data.repositories

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyedMutexTest {

    @Test
    fun sameKeyRunsOneAtATime() = runTest {
        val mutex = KeyedMutex()
        val gate = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()

        val first = async { mutex.withLock("a") { events += "first start"; gate.await(); events += "first end" } }
        val second = async { mutex.withLock("a") { events += "second start" } }
        advanceUntilIdle()

        assertEquals(listOf("first start"), events)
        gate.complete(Unit)
        first.await()
        second.await()
        assertEquals(listOf("first start", "first end", "second start"), events)
    }

    @Test
    fun differentKeysRunInParallel() = runTest {
        val mutex = KeyedMutex()
        val gate = CompletableDeferred<Unit>()
        var started = 0

        val calls = listOf("a", "b", "c").map { key -> async { mutex.withLock(key) { started++; gate.await(); key } } }
        advanceUntilIdle()

        assertEquals(3, started)
        gate.complete(Unit)
        assertEquals(listOf("a", "b", "c"), calls.map { it.await() })
    }

    @Test
    fun theLockIsReleasedWhenTheActionFails() = runTest {
        val mutex = KeyedMutex()

        runCatching { mutex.withLock("a") { error("boom") } }

        assertEquals("ok", mutex.withLock("a") { "ok" })
    }
}
