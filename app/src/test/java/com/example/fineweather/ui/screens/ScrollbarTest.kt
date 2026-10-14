package com.example.fineweather.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScrollbarTest {

    @Test
    fun scrollbarThumb_isNullWhenTheContentFits() {
        assertNull(scrollbarThumb(viewportPx = 500f, contentPx = 500f, scrollPx = 0f, minThumbPx = 24f))
        assertNull(scrollbarThumb(viewportPx = 500f, contentPx = 300f, scrollPx = 0f, minThumbPx = 24f))
    }

    @Test
    fun scrollbarThumb_startsAtTheTopOfTheTrack() {
        val thumb = scrollbarThumb(viewportPx = 500f, contentPx = 1000f, scrollPx = 0f, minThumbPx = 24f)!!

        assertEquals(0f, thumb.top, 1e-3f)
    }

    @Test
    fun scrollbarThumb_endsAtTheBottomOfTheTrackWhenFullyScrolled() {
        val thumb = scrollbarThumb(viewportPx = 500f, contentPx = 1000f, scrollPx = 500f, minThumbPx = 24f)!!

        assertEquals(500f, thumb.top + thumb.height, 1e-3f)
    }

    @Test
    fun scrollbarThumb_heightIsProportionalToTheVisibleShare() {
        val half = scrollbarThumb(viewportPx = 500f, contentPx = 1000f, scrollPx = 0f, minThumbPx = 24f)!!
        val quarter = scrollbarThumb(viewportPx = 500f, contentPx = 2000f, scrollPx = 0f, minThumbPx = 24f)!!

        assertEquals(250f, half.height, 1e-3f)
        assertEquals(125f, quarter.height, 1e-3f)
    }

    @Test
    fun scrollbarThumb_movesLinearlyWithTheScrollPosition() {
        val thumb = scrollbarThumb(viewportPx = 500f, contentPx = 1000f, scrollPx = 250f, minThumbPx = 24f)!!

        // Halfway through the scroll range puts the thumb halfway through its travel (250 of 500).
        assertEquals(125f, thumb.top, 1e-3f)
    }

    @Test
    fun scrollbarThumb_neverShrinksBelowTheMinimumHeight() {
        val thumb = scrollbarThumb(viewportPx = 500f, contentPx = 100_000f, scrollPx = 0f, minThumbPx = 24f)!!

        assertEquals(24f, thumb.height, 1e-3f)
    }

    @Test
    fun scrollbarThumb_staysInsideTheTrackWhenOverscrolled() {
        val past = scrollbarThumb(viewportPx = 500f, contentPx = 1000f, scrollPx = 900f, minThumbPx = 24f)!!
        val before = scrollbarThumb(viewportPx = 500f, contentPx = 1000f, scrollPx = -50f, minThumbPx = 24f)!!

        assertEquals(500f, past.top + past.height, 1e-3f)
        assertEquals(0f, before.top, 1e-3f)
    }

    @Test
    fun scrollbarThumb_keepsTheEndInsetAtBothEnds() {
        val top = scrollbarThumb(viewportPx = 500f, contentPx = 1000f, scrollPx = 0f, minThumbPx = 24f, insetPx = 10f)!!
        val bottom = scrollbarThumb(viewportPx = 500f, contentPx = 1000f, scrollPx = 500f, minThumbPx = 24f, insetPx = 10f)!!

        assertEquals(10f, top.top, 1e-3f)
        assertEquals(490f, bottom.top + bottom.height, 1e-3f)
    }

    @Test
    fun scrollbarThumb_isNullWhenTheTrackHasNoRoom() {
        assertNull(scrollbarThumb(viewportPx = 20f, contentPx = 100f, scrollPx = 0f, minThumbPx = 24f, insetPx = 10f))
    }

    @Test
    fun scrollbarThumb_clampedToTheMinimumHeightStillReachesBothEndsOfTheTrack() {
        val top = scrollbarThumb(viewportPx = 500f, contentPx = 100_000f, scrollPx = 0f, minThumbPx = 24f)!!
        val bottom = scrollbarThumb(viewportPx = 500f, contentPx = 100_000f, scrollPx = 99_500f, minThumbPx = 24f)!!

        assertEquals(0f, top.top, 1e-3f)
        assertEquals(500f, bottom.top + bottom.height, 1e-3f)
    }

    @Test
    fun scrollbarThumb_heightIsProportionalToTheTrackBetweenTheInsets() {
        val thumb = scrollbarThumb(viewportPx = 500f, contentPx = 1000f, scrollPx = 0f, minThumbPx = 24f, insetPx = 10f)!!

        // Half of the content is visible, so the thumb covers half of the 480px track.
        assertEquals(240f, thumb.height, 1e-3f)
    }
}
