package com.example.fineweather.ui.screens

import androidx.compose.animation.core.Animatable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesHintTest {

    @Test
    fun shouldHintFavorites_whenTheListWasNeverOpened() {
        assertTrue(shouldHintFavorites(hasOpenedFavorites = false))
    }

    @Test
    fun shouldHintFavorites_neverOnceTheListWasOpened() {
        assertFalse(shouldHintFavorites(hasOpenedFavorites = true))
    }

    @Test
    fun shouldHintFavorites_notWhileTheFlagIsStillLoading() {
        assertFalse(shouldHintFavorites(hasOpenedFavorites = null))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun playFavoritesHint_withoutAnimations_holdsAStaticHighlightThenRests() = runTest {
        val highlight = Animatable(0f)

        val hint = launch { playFavoritesHint(highlight, animated = false) }
        runCurrent()
        assertEquals(1f, highlight.value, 0f)

        advanceTimeBy(600)
        assertEquals(1f, highlight.value, 0f)

        advanceTimeBy(200)
        hint.join()
        assertEquals(0f, highlight.value, 0f)
    }
}
