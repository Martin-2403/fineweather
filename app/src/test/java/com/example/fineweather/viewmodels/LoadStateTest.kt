package com.example.fineweather.viewmodels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LoadStateTest {

    private val unavailable = Loadable.Unavailable(Reason.NO_DATA_YET)
    private val failed = Loadable.Failed(FailKind.NETWORK)

    @Test
    fun valueOrNull_isOnlyThereForReady() {
        assertEquals(1, Loadable.Ready(1).valueOrNull())
        listOf(Loadable.Idle, Loadable.Loading, unavailable, failed).forEach { assertNull(it.valueOrNull()) }
    }

    @Test
    fun map_transformsReadyAndKeepsEveryOtherState() {
        assertEquals(Loadable.Ready(2), Loadable.Ready(1).map { it + 1 })
        assertEquals(Loadable.Idle, Loadable.Idle.map { 1 })
        assertEquals(Loadable.Loading, Loadable.Loading.map { 1 })
        assertEquals(unavailable, unavailable.map { 1 })
        assertEquals(failed, failed.map { 1 })
    }

    @Test
    fun combine_isReadyOnlyWhenBothAre() {
        assertEquals(Loadable.Ready(3), combineLoadables(Loadable.Ready(1), Loadable.Ready(2)) { a, b -> a + b })
    }

    @Test
    fun combine_waitsWhileEitherIsLoading() {
        assertEquals(Loadable.Loading, combineLoadables(Loadable.Loading, failed) { _: Int, _: Int -> 0 })
        assertEquals(Loadable.Loading, combineLoadables(unavailable, Loadable.Loading) { _: Int, _: Int -> 0 })
        assertEquals(Loadable.Loading, combineLoadables(Loadable.Ready(1), Loadable.Loading) { a, b: Int -> a + b })
    }

    @Test
    fun combine_otherwiseReportsTheOneWithoutValue() {
        assertEquals(failed, combineLoadables(failed, unavailable) { _: Int, _: Int -> 0 })
        assertEquals(unavailable, combineLoadables(Loadable.Ready(1), unavailable) { a, b: Int -> a + b })
        assertEquals(Loadable.Idle, combineLoadables(Loadable.Idle, Loadable.Idle) { _: Int, _: Int -> 0 })
    }
}
