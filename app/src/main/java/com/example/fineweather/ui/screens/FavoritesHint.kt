package com.example.fineweather.ui.screens

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import kotlinx.coroutines.delay

/**
 * Whether saving a favorite should point at the favorites list: only until the list was opened once.
 * [hasOpenedFavorites] is null until the stored flag is read; then nothing is hinted.
 */
internal fun shouldHintFavorites(hasOpenedFavorites: Boolean?): Boolean = hasOpenedFavorites == false

/** Whether the system plays animations; the "remove animations" accessibility option sets the scale to 0. */
internal fun animationsEnabled(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f

private const val PULSE_COUNT = 2
private const val PULSE_UP_MILLIS = 180
private const val PULSE_DOWN_MILLIS = 220
private const val STATIC_HIGHLIGHT_MILLIS = 700L

/**
 * Plays the hint on [highlight] (0 = resting, 1 = fully highlighted): two short pulses of about 800 ms
 * in all. Without animations it holds the highlight for a moment instead, so the hint stays visible.
 */
internal suspend fun playFavoritesHint(
    highlight: Animatable<Float, *>,
    animated: Boolean,
) {
    if (!animated) {
        highlight.snapTo(1f)
        delay(STATIC_HIGHLIGHT_MILLIS)
        highlight.snapTo(0f)
        return
    }
    repeat(PULSE_COUNT) {
        highlight.animateTo(1f, tween(PULSE_UP_MILLIS))
        highlight.animateTo(0f, tween(PULSE_DOWN_MILLIS))
    }
}
