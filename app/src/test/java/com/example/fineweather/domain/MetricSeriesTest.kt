package com.example.fineweather.domain

import com.example.fineweather.data.models.Daily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MetricSeriesTest {

    private val start = LocalDate.of(2026, 10, 14)
    private val times = listOf("2026-10-14", "2026-10-15", "2026-10-16")

    private fun daily(
        temperature: List<Double?>? = listOf(10.0, 11.0, null),
        sunshine: List<Double?>? = listOf(7200.0, 3600.0, 0.0),
        rain: List<Double?>? = listOf(0.0, 2.5, 1.0),
    ) = Daily(times, temperature, sunshine, rain)

    @Test
    fun fromApi_buildsEveryMetricInItsDomainUnit() {
        val series = MetricSeries.fromApi(start, start.plusDays(2), daily())

        assertEquals(start, series.start)
        assertEquals(start.plusDays(2), series.end)
        assertEquals(Metric.entries.toSet(), series.metrics)
        assertEquals(11.0, series[Metric.TEMPERATURE]!!.valueOrNull(start.plusDays(1))!!, 1e-6)
        assertEquals(2.0, series[Metric.SUNSHINE]!!.valueOrNull(start)!!, 1e-6)
        assertEquals(1.0, series[Metric.SUNSHINE]!!.valueOrNull(start.plusDays(1))!!, 1e-6)
        assertEquals(2.5, series[Metric.PRECIPITATION]!!.valueOrNull(start.plusDays(1))!!, 1e-6)
    }

    @Test
    fun fromApi_keepsNullDaysMissingAndZeroAsZero() {
        val series = MetricSeries.fromApi(start, start.plusDays(2), daily())

        assertNull(series[Metric.TEMPERATURE]!!.valueOrNull(start.plusDays(2)))
        assertEquals(0.0, series[Metric.SUNSHINE]!!.valueOrNull(start.plusDays(2))!!, 0.0)
        assertEquals(0.0, series[Metric.PRECIPITATION]!!.valueOrNull(start)!!, 0.0)
    }

    @Test
    fun fromApi_aVariableTheResponseLacksIsAbsentButStillStorable() {
        val series = MetricSeries.fromApi(start, start.plusDays(2), daily(sunshine = null))

        assertNull(series[Metric.SUNSHINE])
        assertFalse(Metric.SUNSHINE in series.metrics)
        assertFalse(Metric.SUNSHINE in series.toMap())
        assertTrue(series[Metric.TEMPERATURE]!!.hasValues())
        // the blob is three missing days, so it can be stored and is not asked for again
        val blob = series.toBytes(Metric.SUNSHINE)!!
        assertEquals(3, DailySeries.fromBytes(start, blob).size)
        assertFalse(DailySeries.fromBytes(start, blob).hasValues())
    }

    @Test
    fun absentMeansNoData_aSeriesOfOnlyNullsIsLeftOut() {
        val series = MetricSeries.fromApi(start, start.plusDays(2), daily(rain = listOf(null, null, null)))

        assertNull(series[Metric.PRECIPITATION])
        assertEquals(setOf(Metric.TEMPERATURE, Metric.SUNSHINE), series.metrics)
        assertEquals(series.metrics, series.toMap().keys)
        assertEquals(3, series.size)
        assertEquals(start.plusDays(2), series.end)
    }

    @Test
    fun fromApi_aListLongerThanTheRangeIsCutAtTheRange() {
        val long = Daily(times + "2026-10-17" + "2026-10-18", listOf(1.0, 2.0, 3.0, 4.0, 5.0), null, null)

        val series = MetricSeries.fromApi(start, start.plusDays(2), long, listOf(Metric.TEMPERATURE))

        assertEquals(3, series.size)
        assertEquals(start.plusDays(2), series.end)
        assertNull(series[Metric.TEMPERATURE]!!.valueOrNull(start.plusDays(3)))
    }

    @Test
    fun fromApi_aListShorterThanTheTimesLeavesTheLastDaysMissing() {
        val series = MetricSeries.fromApi(start, start.plusDays(2), daily(sunshine = listOf(3600.0)))

        assertEquals(3, series.size)
        assertEquals(1.0, series[Metric.SUNSHINE]!!.valueOrNull(start)!!, 0.0)
        assertNull(series[Metric.SUNSHINE]!!.valueOrNull(start.plusDays(1)))
    }

    @Test
    fun fromApi_onlyBuildsTheRequestedMetrics() {
        val series =
            MetricSeries.fromApi(start, start.plusDays(2), daily(temperature = null), listOf(Metric.SUNSHINE, Metric.PRECIPITATION))

        assertEquals(setOf(Metric.SUNSHINE, Metric.PRECIPITATION), series.metrics)
        assertNull(series[Metric.TEMPERATURE])
    }

    @Test
    fun get_isNullForAMetricThatWasNotGiven() {
        val series = MetricSeries(mapOf(Metric.TEMPERATURE to seriesOf(start, start.plusDays(1)) { 1.0 }))

        assertNull(series[Metric.PRECIPITATION])
        assertEquals(1.0, series[Metric.TEMPERATURE]!!.valueOrNull(start)!!, 0.0)
    }

    @Test
    fun construction_requiresOneSharedStartDateAndLength() {
        assertThrows(IllegalArgumentException::class.java) {
            MetricSeries(
                mapOf(
                    Metric.TEMPERATURE to seriesOf(start, start.plusDays(1)) { 1.0 },
                    Metric.SUNSHINE to seriesOf(start, start.plusDays(2)) { 1.0 },
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            MetricSeries(
                mapOf(
                    Metric.TEMPERATURE to seriesOf(start, start.plusDays(1)) { 1.0 },
                    Metric.SUNSHINE to seriesOf(start.plusDays(1), start.plusDays(2)) { 1.0 },
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) { MetricSeries(emptyMap()) }
    }

    @Test
    fun dailyValues_looksUpTheRawListOfAMetric() {
        val daily = daily()

        assertEquals(daily.temperature_2m_mean, daily.values(Metric.TEMPERATURE))
        assertEquals(daily.sunshine_duration, daily.values(Metric.SUNSHINE))
        assertEquals(daily.precipitation_sum, daily.values(Metric.PRECIPITATION))
        assertNull(daily(rain = null).values(Metric.PRECIPITATION))
    }
}
