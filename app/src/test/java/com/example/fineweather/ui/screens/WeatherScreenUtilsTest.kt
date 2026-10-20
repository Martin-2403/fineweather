package com.example.fineweather.ui.screens

import androidx.compose.ui.graphics.Color
import com.example.fineweather.R
import com.example.fineweather.data.models.TemperatureComparisonMode
import com.example.fineweather.domain.Assessment
import com.example.fineweather.domain.ForecastOutlook
import com.example.fineweather.domain.Metric
import com.example.fineweather.domain.SummaryBlock
import com.example.fineweather.viewmodels.FailKind
import com.example.fineweather.viewmodels.Loadable
import com.example.fineweather.viewmodels.PlaceLabel
import com.example.fineweather.viewmodels.Reason
import com.example.fineweather.viewmodels.SearchStatus
import com.example.fineweather.viewmodels.StatusPicture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherScreenUtilsTest {

    private fun divergence(
        delta: Double,
        stdDev: Double? = null,
        mode: TemperatureComparisonMode = TemperatureComparisonMode.ABSOLUTE_DELTA,
    ) = Loadable.Ready(Assessment(delta, stdDev, mode))

    @Test
    fun buildStatusCardValue_returnsFireForPositiveDelta() {
        val value = buildStatusCardValue(divergence(2.0))
        assertEquals("+2.00°C", value.text)
        assertEquals(R.drawable.fire, value.icons?.resId)
        assertEquals(2, value.icons?.count)
        assertEquals(Color(0xFFE53935), value.icons?.tint)
    }

    @Test
    fun buildStatusCardValue_returnsSnowForNegativeDelta() {
        val value = buildStatusCardValue(divergence(-2.0))
        assertEquals("-2.00°C", value.text)
        assertEquals(R.drawable.frost, value.icons?.resId)
        assertEquals(2, value.icons?.count)
        assertEquals(Color(0xFF1E88E5), value.icons?.tint)
    }

    @Test
    fun buildStatusCardValue_returnsApproxZeroWhenClose() {
        val value = buildStatusCardValue(divergence(0.3))
        assertEquals("+0.30°C", value.text)
        assertEquals(null, value.icons)
        assertFalse(value.placeholder)
    }

    @Test
    fun buildStatusCardValue_usesAnomalyBandsForIcons() {
        val value =
            buildStatusCardValue(divergence(1.36, stdDev = 0.8, mode = TemperatureComparisonMode.NORMALIZED_ANOMALY))
        assertEquals("+1.36°C", value.text)
        assertEquals(R.drawable.fire, value.icons?.resId)
        assertEquals(2, value.icons?.count)
    }

    @Test
    fun buildStatusCardValue_isAPlaceholderWithoutAnAssessment() {
        listOf(
            Loadable.Idle,
            Loadable.Unavailable(Reason.NOT_AVAILABLE),
            Loadable.Failed(FailKind.NETWORK),
        ).forEach { state ->
            val value = buildStatusCardValue(state)
            assertEquals("-", value.text)
            assertEquals(null, value.icons)
            assertTrue(value.placeholder)
        }
        val loading = buildStatusCardValue(Loadable.Loading)
        assertEquals("", loading.text)
        assertTrue(loading.loading)
        assertFalse(buildStatusCardValue(Loadable.Idle).loading)
    }

    private fun outlook(delta: Double?) =
        Loadable.Ready(ForecastOutlook(days = 7, mean = 10.0 + (delta ?: 0.0), referenceMean = delta?.let { 10.0 }))

    @Test
    fun buildTrendCardValue_returnsWarmerCoolerOrSteadyAgainstTheNormal() {
        val warmer = buildTrendCardValue(outlook(1.5))
        assertEquals("+1.5°C", warmer.text)
        assertEquals(R.drawable.up, warmer.icons?.resId)
        assertEquals(1, warmer.icons?.count)
        assertEquals(Color(0xFFE53935), warmer.icons?.tint)
        assertEquals("Forecast warmer than normal", warmer.icons?.contentDescription)

        val cooler = buildTrendCardValue(outlook(-2.4))
        assertEquals("-2.4°C", cooler.text)
        assertEquals(R.drawable.down, cooler.icons?.resId)
        assertEquals(2, cooler.icons?.count)
        assertEquals(Color(0xFF1E88E5), cooler.icons?.tint)
        assertEquals("Forecast cooler than normal", cooler.icons?.contentDescription)

        val much = buildTrendCardValue(outlook(3.5))
        assertEquals(3, much.icons?.count)

        val steady = buildTrendCardValue(outlook(0.6))
        assertEquals("+0.6°C", steady.text)
        assertEquals(R.drawable.arrow_right, steady.icons?.resId)
        assertEquals(1, steady.icons?.count)
        assertEquals(Color(0xFF43A047), steady.icons?.tint)
        assertEquals("Forecast near normal", steady.icons?.contentDescription)
    }

    @Test
    fun buildTrendCardValue_returnsDashWithoutAnOutlookOrReference() {
        assertEquals("-", buildTrendCardValue(Loadable.Idle).text)
        assertEquals("-", buildTrendCardValue(Loadable.Failed(FailKind.OTHER)).text)
        assertEquals("-", buildTrendCardValue(outlook(null)).text)
        assertEquals(null, buildTrendCardValue(Loadable.Idle).icons)
        val loading = buildTrendCardValue(Loadable.Loading)
        assertEquals("", loading.text)
        assertTrue(loading.loading)
        assertFalse(buildTrendCardValue(Loadable.Idle).loading)
    }

    @Test
    fun resolveStatusImageRes_picksThePictureOfTheReport() {
        assertEquals(R.drawable.initial, resolveStatusImageRes(StatusPicture.INITIAL))
        assertEquals(R.drawable.initial, resolveStatusImageRes(StatusPicture.LOADING))
        assertEquals(R.drawable.warm, resolveStatusImageRes(StatusPicture.WARM))
        assertEquals(R.drawable.cold, resolveStatusImageRes(StatusPicture.COLD))
        assertEquals(R.drawable.equal, resolveStatusImageRes(StatusPicture.EQUAL))
    }

    @Test
    fun searchStatus_isShownAsTheOneLineStatus() {
        assertEquals("Enter a location to see temperature averages", SearchStatus.Idle.displayText())
        assertEquals("Searching for Ulm...", SearchStatus.Searching("Ulm").displayText())
        assertEquals("City not found", SearchStatus.NotFound("Ulm").displayText())
        assertEquals(
            "Ulm, Germany (48.40, 9.99)",
            SearchStatus.Resolved(PlaceLabel("Ulm, Germany", "48.40, 9.99")).displayText(),
        )
    }

    @Test
    fun summary_hasNoBlocksWhileLoadingAndTheBlocksWhenReady() {
        assertEquals(emptyList<SummaryBlock>(), Loadable.Loading.toBlocks())
        val blocks = listOf<SummaryBlock>(SummaryBlock.Detail("text"))
        assertEquals(blocks, Loadable.Ready(blocks).toBlocks())
    }
}
