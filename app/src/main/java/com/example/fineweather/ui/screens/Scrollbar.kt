package com.example.fineweather.ui.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Vertical position and size of a scrollbar thumb, in pixels from the top of the viewport. */
internal data class ThumbGeometry(val top: Float, val height: Float)

/**
 * Where to draw the thumb of a vertical scrollbar, or null when the content fits the viewport.
 * The thumb is as tall as the visible share of the content (but at least [minThumbPx]) and
 * travels along a track that is the viewport shortened by [insetPx] at both ends.
 */
internal fun scrollbarThumb(
    viewportPx: Float,
    contentPx: Float,
    scrollPx: Float,
    minThumbPx: Float,
    insetPx: Float = 0f,
): ThumbGeometry? {
    val trackPx = viewportPx - 2 * insetPx
    val maxScrollPx = contentPx - viewportPx
    if (trackPx <= 0f || maxScrollPx <= 0f) return null

    val thumbPx = (trackPx * viewportPx / contentPx).coerceIn(minThumbPx.coerceAtMost(trackPx), trackPx)
    val progress = (scrollPx / maxScrollPx).coerceIn(0f, 1f)
    return ThumbGeometry(top = insetPx + (trackPx - thumbPx) * progress, height = thumbPx)
}

/**
 * Draws a thin scrollbar thumb in [color] at the right edge of the modified box, so users can
 * tell that the content scrolls. Place it before any padding so the thumb stays clear of the content.
 */
internal fun Modifier.verticalScrollbar(
    state: ScrollState,
    color: Color,
    width: Dp = 4.dp,
    minThumbHeight: Dp = 24.dp,
    endInset: Dp = 4.dp,
): Modifier =
    drawWithContent {
        drawContent()
        val widthPx = width.toPx()
        val insetPx = endInset.toPx()
        val thumb = scrollbarThumb(
            viewportPx = size.height,
            contentPx = size.height + state.maxValue,
            scrollPx = state.value.toFloat(),
            minThumbPx = minThumbHeight.toPx(),
            insetPx = insetPx,
        ) ?: return@drawWithContent
        val alpha = if (state.isScrollInProgress) 0.8f else 0.4f
        drawRoundRect(
            color = color.copy(alpha = alpha),
            topLeft = Offset(size.width - widthPx - insetPx, thumb.top),
            size = Size(widthPx, thumb.height),
            cornerRadius = CornerRadius(widthPx / 2),
        )
    }
