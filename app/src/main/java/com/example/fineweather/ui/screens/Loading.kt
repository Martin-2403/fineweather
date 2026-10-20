package com.example.fineweather.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * How placeholders behave: they show [delayMs] after loading began, so quick (cached) loads never
 * flash one, and they pulse only when [animated].
 */
@Immutable
internal data class LoadingConfig(
    val delayMs: Long,
    val animated: Boolean,
)

/**
 * Tests and previews get no delay and no animation: an endless animation keeps a Compose test from
 * ever going idle. The app provides the real values in `MainActivity`.
 */
internal val LocalLoadingConfig = staticCompositionLocalOf { LoadingConfig(delayMs = 0, animated = false) }

internal const val LOADING_DESCRIPTION = "Loading"

/**
 * True [delayMs] after [active] turned true, false as soon as it turns false. Without a delay it is
 * simply [active], from the first composition on.
 */
@Composable
internal fun rememberDelayedFlag(
    active: Boolean,
    delayMs: Long = LocalLoadingConfig.current.delayMs,
): Boolean {
    if (delayMs <= 0) return active
    var elapsed by remember(active) { mutableStateOf(false) }
    LaunchedEffect(active) {
        if (active) {
            delay(delayMs)
            elapsed = true
        }
    }
    return active && elapsed
}

/**
 * A small progress ring. It always takes up [size], but is only drawn once [shown], so the layout
 * around it does not move when it appears. A group of placeholders passes one [shown] to all its
 * parts, so they appear together.
 */
@Composable
internal fun LoadingSpinner(
    size: Dp,
    modifier: Modifier = Modifier,
    description: String = LOADING_DESCRIPTION,
    shown: Boolean = rememberDelayedFlag(active = true),
) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        if (!shown) return@Box
        val ring = Modifier.size(size).semantics { contentDescription = description }
        val color = MaterialTheme.colorScheme.primary
        val track = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TRACK_ALPHA)
        val stroke = (size / 10).coerceIn(2.dp, 4.dp)
        if (LocalLoadingConfig.current.animated) {
            CircularProgressIndicator(modifier = ring, color = color, strokeWidth = stroke, trackColor = track)
        } else {
            CircularProgressIndicator(
                progress = { STATIC_PROGRESS },
                modifier = ring,
                color = color,
                strokeWidth = stroke,
                trackColor = track,
            )
        }
    }
}

/**
 * The opacity of skeleton bars: a slow pulse when animated, a constant otherwise. Read it in the
 * draw phase, so a pulse does not recompose anything.
 */
@Composable
internal fun rememberSkeletonPulse(): State<Float> {
    if (!LocalLoadingConfig.current.animated) return remember { mutableStateOf(SKELETON_ALPHA_STATIC) }
    return rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = SKELETON_ALPHA_MIN,
        targetValue = SKELETON_ALPHA_MAX,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
}

/** A rounded bar standing in for a line of text; blank until [shown]. */
@Composable
internal fun SkeletonLine(
    modifier: Modifier = Modifier,
    width: Dp = Dp.Unspecified,
    fraction: Float = 1f,
    height: Dp = scaledDp(SKELETON_LINE_SP),
    description: String? = null,
    shown: Boolean = rememberDelayedFlag(active = true),
    pulse: State<Float> = rememberSkeletonPulse(),
) {
    val size = if (width == Dp.Unspecified) Modifier.fillMaxWidth(fraction) else Modifier.width(width)
    val look =
        if (shown) {
            Modifier
                .graphicsLayer { alpha = pulse.value }
                .background(MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(height / 2))
                .semantics { description?.let { contentDescription = it } }
        } else {
            Modifier
        }
    Box(modifier.then(size).height(height).then(look))
}

/**
 * Skeleton lines under one [description] (none for a block next to one that has it), as tall as lines of
 * body text, standing in for a block of text.
 */
@Composable
internal fun SkeletonBlock(
    description: String?,
    modifier: Modifier = Modifier,
    fractions: List<Float> = listOf(0.85f, 1f, 0.6f),
) {
    val shown = rememberDelayedFlag(active = true)
    val pulse = rememberSkeletonPulse()
    Column(
        modifier =
            modifier.then(
                if (shown && description != null) Modifier.semantics { contentDescription = description } else Modifier,
            ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        fractions.forEach {
            Box(Modifier.fillMaxWidth().height(scaledDp(BODY_LINE_SP)), contentAlignment = Alignment.CenterStart) {
                SkeletonLine(fraction = it, shown = shown, pulse = pulse)
            }
        }
    }
}

/** A neutral tile with a centered spinner, for a picture that is still on its way. */
@Composable
internal fun LoadingPanel(
    description: String,
    modifier: Modifier = Modifier,
    spinnerSize: Dp = 32.dp,
) {
    val shown = rememberDelayedFlag(active = true)
    Box(modifier.padding(8.dp), contentAlignment = Alignment.Center) {
        if (shown) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TILE_ALPHA),
                        RoundedCornerShape(10.dp),
                    ),
            )
        }
        LoadingSpinner(spinnerSize, description = description, shown = shown)
    }
}

/** A spinner centered in an area as high as the content that will replace it. */
@Composable
internal fun LoadingArea(
    height: Dp,
    modifier: Modifier = Modifier,
    description: String = LOADING_DESCRIPTION,
    spinnerSize: Dp = 36.dp,
) {
    Box(modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
        LoadingSpinner(size = spinnerSize, description = description)
    }
}

/** [sp] as a dp size at the current font scale, so placeholders grow with the text they replace. */
@Composable
internal fun scaledDp(sp: Float): Dp = with(LocalDensity.current) { sp.sp.toDp() }

private const val STATIC_PROGRESS = 0.25f
private const val TRACK_ALPHA = 0.15f
private const val TILE_ALPHA = 0.08f
private const val SKELETON_ALPHA_STATIC = 0.18f
private const val SKELETON_ALPHA_MIN = 0.1f
private const val SKELETON_ALPHA_MAX = 0.25f
private const val SKELETON_LINE_SP = 14f
private const val BODY_LINE_SP = 20f
