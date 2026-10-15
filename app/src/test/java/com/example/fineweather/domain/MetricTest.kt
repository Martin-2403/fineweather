package com.example.fineweather.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MetricTest {

    @Test
    fun metricsDescribeThemselvesAndTheirApiVariable() {
        assertEquals(listOf("Temp", "Sun", "Rain"), Metric.entries.map { it.label })
        assertEquals(listOf("°C", "h/day", "mm"), Metric.entries.map { it.unit })
        assertEquals(
            listOf("temperature_2m_mean", "sunshine_duration", "precipitation_sum"),
            Metric.entries.map { it.apiName },
        )
    }

    @Test
    fun onlySunshineIsConvertedFromSecondsToHours() {
        assertEquals(1.0, Metric.TEMPERATURE.apiDivisor, 0.0)
        assertEquals(1.0, Metric.PRECIPITATION.apiDivisor, 0.0)
        assertEquals(3600.0, Metric.SUNSHINE.apiDivisor, 0.0)
        // dividing keeps whole hours exact
        assertEquals(2.0, 7200.0 / Metric.SUNSHINE.apiDivisor, 0.0)
    }
}
