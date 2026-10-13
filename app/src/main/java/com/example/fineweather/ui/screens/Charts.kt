package com.example.fineweather.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fineweather.domain.DayPoint
import com.example.fineweather.domain.MonthlyAnomaly
import kotlin.math.abs

/**
 * Daily means as a line over the reference's typical range (mean ± 1 standard deviation).
 * Dots are tinted by whether the day was warmer or colder than the reference mean.
 */
@Composable
internal fun DailyChart(
    points: List<DayPoint>,
    xLabels: List<Pair<Int, String>>,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = textColor.copy(alpha = 0.15f)
    val bandColor = textColor.copy(alpha = 0.16f)
    val lineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(height)
                .semantics { contentDescription = description },
    ) {
        val left = 34.dp.toPx()
        val right = 8.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = 20.dp.toPx()
        val width = size.width - left - right
        val chartHeight = size.height - top - bottom

        val values =
            points.flatMap { point ->
                listOfNotNull(
                    point.observed,
                    point.band?.let { it.mean + it.stdDev },
                    point.band?.let { it.mean - it.stdDev },
                )
            }
        if (values.isEmpty() || points.isEmpty()) return@Canvas
        val axis = niceAxis(values.min(), values.max())

        fun x(index: Int): Float =
            if (points.size == 1) left + width / 2 else left + width * index / (points.size - 1f)

        fun y(value: Double): Float =
            top + chartHeight * (1f - ((value - axis.min) / (axis.max - axis.min)).toFloat())

        axis.ticks.forEach { tick ->
            drawLine(gridColor, Offset(left, y(tick)), Offset(left + width, y(tick)), 1.dp.toPx())
            val layout = measurer.measure(formatTick(tick), labelStyle)
            drawText(
                layout,
                color = textColor,
                topLeft = Offset(left - 6.dp.toPx() - layout.size.width, y(tick) - layout.size.height / 2f),
            )
        }

        val withBand = points.withIndex().filter { it.value.band != null }
        if (withBand.size >= 2) {
            val band =
                Path().apply {
                    withBand.forEachIndexed { k, (i, point) ->
                        val upper = y(point.band!!.mean + point.band.stdDev)
                        if (k == 0) moveTo(x(i), upper) else lineTo(x(i), upper)
                    }
                    withBand.asReversed().forEach { (i, point) ->
                        lineTo(x(i), y(point.band!!.mean - point.band.stdDev))
                    }
                    close()
                }
            drawPath(band, bandColor)
            val mean =
                Path().apply {
                    withBand.forEachIndexed { k, (i, point) ->
                        if (k == 0) moveTo(x(i), y(point.band!!.mean)) else lineTo(x(i), y(point.band!!.mean))
                    }
                }
            drawPath(
                mean,
                textColor.copy(alpha = 0.55f),
                style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
            )
        }

        val observed = Path()
        var drawing = false
        points.forEachIndexed { index, point ->
            val value = point.observed
            if (value == null) {
                drawing = false
            } else {
                if (drawing) observed.lineTo(x(index), y(value)) else observed.moveTo(x(index), y(value))
                drawing = true
            }
        }
        drawPath(observed, lineColor, style = Stroke(2.dp.toPx()))
        points.forEachIndexed { index, point ->
            val value = point.observed ?: return@forEachIndexed
            val reference = point.band?.mean
            val tint =
                when {
                    reference == null -> lineColor
                    value > reference -> WarmIconTint
                    value < reference -> CoolIconTint
                    else -> NeutralIconTint
                }
            drawCircle(tint, radius = 3.dp.toPx(), center = Offset(x(index), y(value)))
        }

        xLabels.forEach { (index, label) ->
            val layout = measurer.measure(label, labelStyle)
            val centered = x(index) - layout.size.width / 2f
            drawText(
                layout,
                color = textColor,
                topLeft =
                    Offset(
                        centered.coerceIn(0f, size.width - layout.size.width),
                        top + chartHeight + 4.dp.toPx(),
                    ),
            )
        }
    }
}

/** One bar per month for the deviation from the reference; unfinished months are drawn lighter. */
@Composable
internal fun AnomalyChart(
    months: List<MonthlyAnomaly>,
    monthInitials: List<String>,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 190.dp,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
    val valueStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp)
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = textColor.copy(alpha = 0.15f)

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(height)
                .semantics { contentDescription = description },
    ) {
        val left = 34.dp.toPx()
        val right = 8.dp.toPx()
        val top = 12.dp.toPx()
        val bottom = 20.dp.toPx()
        val width = size.width - left - right
        val chartHeight = size.height - top - bottom

        val deltas = months.mapNotNull { it.delta }
        val axis =
            niceAxis(
                minOf(0.0, deltas.minOrNull() ?: 0.0),
                maxOf(1.0, deltas.maxOrNull() ?: 0.0),
            )

        fun y(value: Double): Float =
            top + chartHeight * (1f - ((value - axis.min) / (axis.max - axis.min)).toFloat())

        axis.ticks.forEach { tick ->
            val isZero = abs(tick) < 1e-9
            drawLine(
                if (isZero) textColor.copy(alpha = 0.6f) else gridColor,
                Offset(left, y(tick)),
                Offset(left + width, y(tick)),
                if (isZero) 1.5.dp.toPx() else 1.dp.toPx(),
            )
            val layout = measurer.measure(formatTick(tick), labelStyle)
            drawText(
                layout,
                color = textColor,
                topLeft = Offset(left - 6.dp.toPx() - layout.size.width, y(tick) - layout.size.height / 2f),
            )
        }

        val slot = width / 12f
        for (month in 1..12) {
            val center = left + slot * (month - 0.5f)
            val initial = measurer.measure(monthInitials[month - 1], labelStyle)
            drawText(
                initial,
                color = textColor,
                topLeft = Offset(center - initial.size.width / 2f, top + chartHeight + 4.dp.toPx()),
            )
            val anomaly = months.firstOrNull { it.month == month } ?: continue
            val delta = anomaly.delta ?: continue
            val color: Color = if (delta >= 0) WarmIconTint else CoolIconTint
            val barWidth = slot * 0.6f
            val zero = y(0.0)
            val end = y(delta)
            val topLeft = Offset(center - barWidth / 2f, minOf(zero, end))
            val barSize = androidx.compose.ui.geometry.Size(barWidth, abs(end - zero).coerceAtLeast(1.dp.toPx()))
            drawRect(color.copy(alpha = if (anomaly.partial) 0.4f else 0.9f), topLeft, barSize)
            if (anomaly.partial) {
                drawRect(color, topLeft, barSize, style = Stroke(1.5.dp.toPx()))
            }
            val label = measurer.measure(formatSigned(delta), valueStyle)
            val labelY =
                if (delta >= 0) topLeft.y - label.size.height - 1.dp.toPx() else topLeft.y + barSize.height + 1.dp.toPx()
            drawText(
                label,
                color = textColor,
                topLeft = Offset(center - label.size.width / 2f, labelY.coerceIn(0f, size.height - label.size.height)),
            )
        }
    }
}
