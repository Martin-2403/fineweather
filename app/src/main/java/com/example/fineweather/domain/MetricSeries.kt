package com.example.fineweather.domain

import com.example.fineweather.data.models.Daily
import java.time.LocalDate

/**
 * Daily series of several [Metric]s over the same days. Absent means no data: [get], [metrics] and
 * [toMap] leave out a metric that has no value on any day. [toBytes] is the exception, so that
 * a metric without data can still be stored.
 */
class MetricSeries(
    private val byMetric: Map<Metric, DailySeries>,
) {
    init {
        require(byMetric.isNotEmpty()) { "A MetricSeries needs at least one metric" }
        require(byMetric.values.map { it.start }.distinct().size == 1) {
            "All series of a MetricSeries must start on the same day"
        }
        require(byMetric.values.map { it.size }.distinct().size == 1) {
            "All series of a MetricSeries must have the same number of days"
        }
    }

    val start: LocalDate = byMetric.values.first().start

    /** Number of days every series covers, with or without values. */
    val size: Int = byMetric.values.first().size
    val end: LocalDate get() = start.plusDays(size - 1L)

    /** The metrics that have at least one value. */
    val metrics: Set<Metric> get() = toMap().keys

    /** The series of [metric], or null if it was not given or has no value on any day. */
    operator fun get(metric: Metric): DailySeries? = byMetric[metric]?.takeIf { it.hasValues() }

    /** Every series that has at least one value. */
    fun toMap(): Map<Metric, DailySeries> = byMetric.filterValues { it.hasValues() }

    /** The float32 blob of [metric] as stored, also when it has no values; null if it was not given. */
    fun toBytes(metric: Metric): ByteArray? = byMetric[metric]?.toBytes()

    companion object {
        /**
         * Builds the [metrics] covering [start]..[end] from an API response. A variable the response
         * lacks becomes a series of missing days.
         */
        fun fromApi(
            start: LocalDate,
            end: LocalDate,
            daily: Daily,
            metrics: Collection<Metric> = Metric.entries,
        ): MetricSeries =
            MetricSeries(
                metrics.associateWith { metric ->
                    DailySeries.fromApi(
                        start,
                        end,
                        daily.time,
                        daily.values(metric) ?: emptyList(),
                        metric.apiDivisor,
                    )
                },
            )
    }
}
