package com.example.fineweather.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fineweather.domain.DayPoint
import com.example.fineweather.domain.Metric
import com.example.fineweather.domain.MonthlyAnomaly
import kotlin.math.abs

internal val DailyChartHeight = 200.dp
internal val AnomalyChartHeight = 190.dp

/** The plotting area of a chart and the axis that maps values onto it. */
private class ChartArea(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val axis: Axis,
) {
    val bottom: Float get() = top + height

    fun y(value: Double): Float = top + height * (1f - ((value - axis.min) / (axis.max - axis.min)).toFloat())
}

/** How a chart writes its labels. */
private class ChartText(
    val measurer: TextMeasurer,
    val style: TextStyle,
    val color: Color,
) {
    val gridColor: Color = color.copy(alpha = 0.15f)
}

/** Grid lines with the tick labels left of them; with [emphasizeZero] the zero line is stronger. */
private fun DrawScope.drawYAxis(
    area: ChartArea,
    text: ChartText,
    label: (Double) -> String,
    emphasizeZero: Boolean = false,
) {
    area.axis.ticks.forEach { tick ->
        val isZero = emphasizeZero && abs(tick) < 1e-9
        drawLine(
            if (isZero) text.color.copy(alpha = 0.6f) else text.gridColor,
            Offset(area.left, area.y(tick)),
            Offset(area.left + area.width, area.y(tick)),
            if (isZero) 1.5.dp.toPx() else 1.dp.toPx(),
        )
        val layout = text.measurer.measure(label(tick), text.style)
        drawText(
            layout,
            color = text.color,
            topLeft = Offset(area.left - 6.dp.toPx() - layout.size.width, area.y(tick) - layout.size.height / 2f),
        )
    }
}

/** The labels below the chart, each centred under the x of its index. */
private fun DrawScope.drawXLabels(
    area: ChartArea,
    text: ChartText,
    labels: List<Pair<Int, String>>,
    x: (Int) -> Float,
) {
    labels.forEach { (index, label) ->
        val layout = text.measurer.measure(label, text.style)
        val centered = x(index) - layout.size.width / 2f
        drawText(
            layout,
            color = text.color,
            topLeft = Offset(centered.coerceIn(0f, size.width - layout.size.width), area.bottom + 4.dp.toPx()),
        )
    }
}

/** The faint vertical line between the last observed day and the first forecast day. */
private fun DrawScope.drawTodayDivider(
    area: ChartArea,
    color: Color,
    x: Float,
) {
    drawLine(
        color.copy(alpha = 0.3f),
        Offset(x, area.top),
        Offset(x, area.bottom),
        1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)),
    )
}

/** The index of the last observed point and of the first forecast point, if the forecast follows the observations. */
private fun dividerIndices(points: List<DayPoint>): Pair<Int, Int>? {
    val firstForecast = points.indices.firstOrNull { points[it].forecast != null } ?: return null
    val lastObserved = points.indices.lastOrNull { points[it].observed != null } ?: return null
    return if (lastObserved < firstForecast) lastObserved to firstForecast else null
}

/**
 * Daily values over the days of a period. Temperature is a line over the reference's typical range (mean
 * ± 1 standard deviation), its dots tinted by whether the day was warmer or colder than the reference mean.
 * Sunshine and rain use daily bars with cumulative value and historical-average lines. Forecast bars are
 * lighter and outlined.
 */
@Composable
internal fun DailyChart(
    points: List<DayPoint>,
    xLabels: List<Pair<Int, String>>,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = DailyChartHeight,
    metric: Metric = Metric.TEMPERATURE,
    dailyAverageExtension: List<Double?> = emptyList(),
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val text = ChartText(measurer, labelStyle, textColor)
    val lineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
    val forecastColor = MaterialTheme.colorScheme.primary
    val dark = isDarkCard()

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(height)
                .semantics { contentDescription = description },
    ) {
        when (metric) {
            Metric.TEMPERATURE -> {
                drawTemperatureChart(points, xLabels, text, lineColor, forecastColor)
            }
            Metric.SUNSHINE, Metric.PRECIPITATION -> {
                drawAccumulatedDailyChart(
                    points,
                    xLabels,
                    text,
                    metric,
                    dark,
                    lineColor,
                    forecastColor,
                    dailyAverageExtension,
                )
            }
        }
    }
}

private fun DrawScope.drawTemperatureChart(
    points: List<DayPoint>,
    xLabels: List<Pair<Int, String>>,
    text: ChartText,
    lineColor: Color,
    forecastColor: Color,
) {
    val textColor = text.color
    val bandColor = textColor.copy(alpha = 0.16f)
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
                point.forecast,
                point.band?.let { it.mean + it.stdDev },
                point.band?.let { it.mean - it.stdDev },
            )
        }
    if (values.isEmpty() || points.isEmpty()) return
    val area = ChartArea(left, top, width, chartHeight, niceAxis(values.min(), values.max()))

    fun x(index: Int): Float =
        if (points.size == 1) left + width / 2 else left + width * index / (points.size - 1f)

    fun y(value: Double): Float = area.y(value)

    drawYAxis(area, text, ::formatTick)

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

    val forecastIndices = points.indices.filter { points[it].forecast != null }
    if (forecastIndices.isNotEmpty()) {
        val lastObserved = points.indices.lastOrNull { points[it].observed != null }
        val firstForecast = forecastIndices.first()
        dividerIndices(points)?.let { (last, first) -> drawTodayDivider(area, textColor, (x(last) + x(first)) / 2f) }
        val forecast = Path()
        if (lastObserved != null && lastObserved < firstForecast) {
            forecast.moveTo(x(lastObserved), y(points[lastObserved].observed!!))
        }
        forecastIndices.forEachIndexed { k, index ->
            val point = Offset(x(index), y(points[index].forecast!!))
            if (k == 0 && (lastObserved == null || lastObserved >= firstForecast)) {
                forecast.moveTo(point.x, point.y)
            } else {
                forecast.lineTo(point.x, point.y)
            }
        }
        drawPath(
            forecast,
            forecastColor,
            style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
        )
        forecastIndices.forEach { index ->
            val value = points[index].forecast!!
            val reference = points[index].band?.mean
            val tint =
                when {
                    reference == null -> forecastColor
                    value > reference -> WarmIconTint
                    value < reference -> CoolIconTint
                    else -> NeutralIconTint
                }
            drawCircle(
                tint,
                radius = 3.dp.toPx(),
                center = Offset(x(index), y(value)),
                style = Stroke(1.5.dp.toPx()),
            )
        }
    }

    drawXLabels(area, text, xLabels, ::x)
}

/** Daily sunshine/rain bars with a secondary axis for accumulated values and historical average. */
private fun DrawScope.drawAccumulatedDailyChart(
    points: List<DayPoint>,
    xLabels: List<Pair<Int, String>>,
    text: ChartText,
    metric: Metric,
    dark: Boolean,
    lineColor: Color,
    forecastColor: Color,
    averageExtension: List<Double?>,
) {
    val left = 44.dp.toPx()
    val right = 44.dp.toPx()
    val top = 8.dp.toPx()
    val bottom = 20.dp.toPx()
    val width = size.width - left - right
    val chartHeight = size.height - top - bottom
    val dailyValues = points.mapNotNull { it.observed ?: it.forecast }
    val cumulative = cumulativeDailyValues(points)
    val averageCumulative = cumulativeAverageDailyValues(points, averageExtension)
    val cumulativeValues = cumulative.filterNotNull() + averageCumulative.filterNotNull()
    if (dailyValues.isEmpty() || cumulativeValues.isEmpty()) return

    val dailyAxis = dailyBarAxis(metric, dailyValues.max())
    val area = ChartArea(left, top, width, chartHeight, dailyAxis)
    val cumulativeAxis = alignedZeroAxis(cumulativeValues.max(), dailyAxis.ticks.size - 1)
    val slots = barSlots(maxOf(points.size, averageCumulative.size), width, fill = 0.7f, maxBarWidth = 24.dp.toPx())
    fun x(index: Int): Float = slots.center(left, index)
    fun cumulativeY(value: Double): Float =
        top + chartHeight * (1f - ((value - cumulativeAxis.min) / (cumulativeAxis.max - cumulativeAxis.min)).toFloat())

    drawYAxis(area, text, { dailyTickLabel(metric, it) }, emphasizeZero = true)
    drawRightYAxis(area, cumulativeAxis, text) { dailyTickLabel(metric, it) }

    drawCumulativeDailyReference(averageCumulative, text.color, ::x, ::cumulativeY)
    drawDailyBars(points, area, slots, metric, dark)
    drawCumulativeDailyObserved(
        points = points,
        cumulative = cumulative,
        color = lineColor,
        forecastColor = forecastColor,
        x = ::x,
        y = ::cumulativeY,
    )
    dividerIndices(points)?.let { (last, first) -> drawTodayDivider(area, text.color, (x(last) + x(first)) / 2f) }
    drawXLabels(area, text, xLabels, ::x)
}

private fun DrawScope.drawCumulativeDailyReference(
    cumulative: List<Double?>,
    color: Color,
    x: (Int) -> Float,
    y: (Double) -> Float,
) {
    val average = Path()
    var drawing = false
    cumulative.forEachIndexed { index, value ->
        if (value == null) {
            drawing = false
        } else {
            if (drawing) average.lineTo(x(index), y(value)) else average.moveTo(x(index), y(value))
            drawing = true
        }
    }
    drawPath(
        average,
        color.copy(alpha = 0.7f),
        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))),
    )
}

private fun DrawScope.drawRightYAxis(
    area: ChartArea,
    axis: Axis,
    text: ChartText,
    label: (Double) -> String,
) {
    axis.ticks.forEach { tick ->
        val y = area.top + area.height * (1f - ((tick - axis.min) / (axis.max - axis.min)).toFloat())
        val layout = text.measurer.measure(label(tick), text.style)
        drawText(
            layout,
            color = text.color,
            topLeft = Offset(area.left + area.width + 5.dp.toPx(), y - layout.size.height / 2f),
        )
    }
}

private fun DrawScope.drawCumulativeDailyObserved(
    points: List<DayPoint>,
    cumulative: List<Double?>,
    color: Color,
    forecastColor: Color,
    x: (Int) -> Float,
    y: (Double) -> Float,
) {
    val actual = Path()
    var drawingActual = false
    points.forEachIndexed { index, point ->
        val value = cumulative[index]
        if (point.observed == null || value == null) {
            drawingActual = false
        } else {
            if (drawingActual) actual.lineTo(x(index), y(value)) else actual.moveTo(x(index), y(value))
            drawingActual = true
        }
    }
    drawPath(actual, color.copy(alpha = 0.8f), style = Stroke(2.dp.toPx()))

    val forecastIndices = points.indices.filter { points[it].forecast != null && cumulative[it] != null }
    if (forecastIndices.isEmpty()) return
    val forecast = Path()
    val lastObserved = points.indices.lastOrNull { points[it].observed != null && cumulative[it] != null }
    if (lastObserved != null && lastObserved < forecastIndices.first()) {
        forecast.moveTo(x(lastObserved), y(cumulative[lastObserved]!!))
    }
    forecastIndices.forEachIndexed { index, pointIndex ->
        val point = Offset(x(pointIndex), y(cumulative[pointIndex]!!))
        if (index == 0 && (lastObserved == null || lastObserved >= forecastIndices.first())) {
            forecast.moveTo(point.x, point.y)
        } else {
            forecast.lineTo(point.x, point.y)
        }
    }
    drawPath(
        forecast,
        forecastColor,
        style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
    )
}

/** One bar per day with a value: observed ones solid, forecast ones lighter with an outline. */
private fun DrawScope.drawDailyBars(
    points: List<DayPoint>,
    area: ChartArea,
    slots: BarSlots,
    metric: Metric,
    dark: Boolean,
) {
    val stub = 1.5.dp.toPx()
    points.forEachIndexed { index, point ->
        val forecast = point.observed == null
        val value = point.observed ?: point.forecast ?: return@forEachIndexed
        val color = dailyBarColor(metric, dark)
        val barHeight = barHeight(value, area.axis.max, area.height, stub)
        val topLeft = Offset(slots.center(area.left, index) - slots.barWidth / 2f, area.bottom - barHeight)
        val barSize = Size(slots.barWidth, barHeight)
        drawRect(color.copy(alpha = if (forecast) 0.4f else 0.9f), topLeft, barSize)
        if (forecast) drawRect(color, topLeft, barSize, style = Stroke(1.dp.toPx()))
    }
}

/**
 * One bar per month. Temperature shows the deviation from the reference in °C. Sunshine and rain show the
 * month's total in front of a wider, grey bar with the historical average of the same days; unfinished
 * months are drawn lighter.
 */
@Composable
internal fun AnomalyChart(
    months: List<MonthlyAnomaly>,
    monthInitials: List<String>,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = AnomalyChartHeight,
    metric: Metric = Metric.TEMPERATURE,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
    val valueStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp)
    val text = ChartText(measurer, labelStyle, MaterialTheme.colorScheme.onSurfaceVariant)
    val dark = isDarkCard()

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(height)
                .semantics { contentDescription = description },
    ) {
        val left = (if (metric == Metric.TEMPERATURE) 34.dp else 44.dp).toPx()
        val right = 8.dp.toPx()
        val top = 12.dp.toPx()
        val bottom = 20.dp.toPx()
        val width = size.width - left - right
        val chartHeight = size.height - top - bottom
        val bars = months.associate { it.month to anomalyValue(metric, it) }
        val references = months.associate { it.month to anomalyReferenceValue(metric, it) }
        val axisValues = (bars.values + references.values).filterNotNull()
        val area = ChartArea(left, top, width, chartHeight, anomalyAxis(metric, axisValues))

        drawYAxis(area, text, { anomalyTickLabel(metric, it) }, emphasizeZero = true)

        val slot = width / 12f
        for (month in 1..12) {
            val center = left + slot * (month - 0.5f)
            val initial = measurer.measure(monthInitials[month - 1], labelStyle)
            drawText(
                initial,
                color = text.color,
                topLeft = Offset(center - initial.size.width / 2f, area.bottom + 4.dp.toPx()),
            )
            val anomaly = months.firstOrNull { it.month == month } ?: continue
            if (metric == Metric.TEMPERATURE) {
                val value = bars[month] ?: continue
                drawMonthBar(area, text, valueStyle, center, slot * 0.6f, metric, value, anomaly.partial, dark)
            } else {
                drawTotalBars(area, text, valueStyle, center, slot, metric, bars[month], references[month], anomaly.partial, dark)
            }
        }
    }
}

/**
 * A month's total from zero, with its value above, in front of the wider grey bar of the historical average.
 * The label goes above the taller of the two.
 */
private fun DrawScope.drawTotalBars(
    area: ChartArea,
    text: ChartText,
    valueStyle: TextStyle,
    center: Float,
    slot: Float,
    metric: Metric,
    total: Double?,
    reference: Double?,
    partial: Boolean,
    dark: Boolean,
) {
    val minHeight = 1.dp.toPx()
    reference?.let {
        val topLeft = Offset(center - slot * 0.36f, minOf(area.y(it), area.bottom - minHeight))
        val barSize = Size(slot * 0.72f, area.bottom - topLeft.y)
        drawRect(text.color.copy(alpha = 0.18f), topLeft, barSize)
        drawRect(
            text.color.copy(alpha = 0.5f),
            topLeft,
            barSize,
            style = Stroke(1.dp.toPx()),
        )
    }
    var labelTop = reference?.let { minOf(area.y(it), area.bottom - minHeight) } ?: area.bottom
    total?.let {
        val color = dailyBarColor(metric, dark)
        val topLeft = Offset(center - slot * 0.25f, minOf(area.y(it), area.bottom - minHeight))
        val barSize = Size(slot * 0.5f, area.bottom - topLeft.y)
        drawRect(color.copy(alpha = if (partial) 0.4f else 0.9f), topLeft, barSize)
        if (partial) drawRect(color, topLeft, barSize, style = Stroke(1.5.dp.toPx()))
        labelTop = minOf(labelTop, topLeft.y)
        val label = text.measurer.measure(anomalyBarLabel(metric, it), valueStyle)
        drawText(
            label,
            color = text.color,
            topLeft = Offset(center - label.size.width / 2f, (labelTop - label.size.height - 1.dp.toPx()).coerceAtLeast(0f)),
        )
    }
}

/** One month's bar from zero to [value], with its value above it (below it for a negative one). */
private fun DrawScope.drawMonthBar(
    area: ChartArea,
    text: ChartText,
    valueStyle: TextStyle,
    center: Float,
    barWidth: Float,
    metric: Metric,
    value: Double,
    partial: Boolean,
    dark: Boolean,
) {
    val color = anomalyBarColor(metric, value, dark)
    val zero = area.y(0.0)
    val end = area.y(value)
    val topLeft = Offset(center - barWidth / 2f, minOf(zero, end))
    val barSize = Size(barWidth, abs(end - zero).coerceAtLeast(1.dp.toPx()))
    drawRect(color.copy(alpha = if (partial) 0.4f else 0.9f), topLeft, barSize)
    if (partial) {
        drawRect(color, topLeft, barSize, style = Stroke(1.5.dp.toPx()))
    }
    val label = text.measurer.measure(anomalyBarLabel(metric, value), valueStyle)
    val labelY = if (value >= 0) topLeft.y - label.size.height - 1.dp.toPx() else topLeft.y + barSize.height + 1.dp.toPx()
    drawText(
        label,
        color = text.color,
        topLeft = Offset(center - label.size.width / 2f, labelY.coerceIn(0f, size.height - label.size.height)),
    )
}

/** A small sample of a daily-chart element for the legend, drawn with the chart's colors and strokes. */
@Composable
internal fun LegendSwatch(
    symbol: LegendSymbol,
    metric: Metric,
    modifier: Modifier = Modifier,
) {
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val lineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
    val forecastColor = MaterialTheme.colorScheme.primary
    val barColor = dailyBarColor(metric, isDarkCard())

    Canvas(modifier.size(LegendSwatchWidth, LegendSwatchHeight)) {
        val midY = size.height / 2f
        val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
        val dot = 3.dp.toPx()
        fun line(
            color: Color,
            width: Dp,
            effect: PathEffect? = null,
            y: Float = midY,
        ) = drawLine(color, Offset(0f, y), Offset(size.width, y), width.toPx(), pathEffect = effect)

        fun bar(
            centerX: Float,
            heightFraction: Float,
            forecast: Boolean,
        ) {
            val barWidth = 6.dp.toPx()
            val barHeight = size.height * heightFraction
            val topLeft = Offset(centerX - barWidth / 2f, size.height - barHeight)
            val barSize = Size(barWidth, barHeight)
            drawRect(barColor.copy(alpha = if (forecast) 0.4f else 0.9f), topLeft, barSize)
            if (forecast) drawRect(barColor, topLeft, barSize, style = Stroke(1.dp.toPx()))
        }

        when (symbol) {
            LegendSymbol.DAILY_LINE -> {
                line(lineColor, 2.dp)
                drawCircle(lineColor, dot, Offset(size.width / 2f, midY))
            }
            LegendSymbol.WARM_COLD_DOTS -> {
                drawCircle(WarmIconTint, dot, Offset(size.width * 0.3f, midY))
                drawCircle(CoolIconTint, dot, Offset(size.width * 0.7f, midY))
            }
            LegendSymbol.TYPICAL_BAND -> {
                drawRect(textColor.copy(alpha = 0.16f), Offset(0f, size.height * 0.15f), Size(size.width, size.height * 0.7f))
                line(textColor.copy(alpha = 0.55f), 1.5.dp, dash)
            }
            LegendSymbol.TEMPERATURE_FORECAST -> {
                line(forecastColor, 2.dp, dash)
                drawCircle(forecastColor, dot, Offset(size.width / 2f, midY), style = Stroke(1.5.dp.toPx()))
            }
            LegendSymbol.DAILY_BARS -> {
                bar(size.width * 0.3f, 0.55f, forecast = false)
                bar(size.width * 0.7f, 0.85f, forecast = false)
            }
            LegendSymbol.ACCUMULATED_LINE -> line(lineColor.copy(alpha = 0.8f), 2.dp)
            LegendSymbol.AVERAGE_LINE ->
                line(textColor.copy(alpha = 0.7f), 1.5.dp, PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
            LegendSymbol.BAR_FORECAST -> {
                bar(size.width * 0.25f, 0.6f, forecast = true)
                drawLine(
                    forecastColor,
                    Offset(size.width * 0.45f, size.height * 0.75f),
                    Offset(size.width, size.height * 0.2f),
                    2.dp.toPx(),
                    pathEffect = dash,
                )
            }
        }
    }
}

private val LegendSwatchWidth = 28.dp
private val LegendSwatchHeight = 16.dp
