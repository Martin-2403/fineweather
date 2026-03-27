package com.example.fineweather.data.repositories

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.fineweather.data.models.HistoricReference
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempFile

class SettingsRepositoryTest {

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun defaults_areExposedWhenEmpty() = runTest {
        val repository = DataStoreSettingsRepository(createDataStore(this))

        assertEquals(SettingsDefaults.DEFAULT_FORECAST_DAYS, repository.forecastDays.first())
        assertEquals(SettingsDefaults.DEFAULT_HISTORIC_REFERENCE, repository.historicReference.first())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun setForecastDays_updatesFlow() = runTest {
        val repository = DataStoreSettingsRepository(createDataStore(this))

        repository.setForecastDays(SettingsDefaults.FORECAST_DAYS_LONG)

        assertEquals(SettingsDefaults.FORECAST_DAYS_LONG, repository.forecastDays.first())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun setForecastDays_invalidValuesFallbackToDefault() = runTest {
        val repository = DataStoreSettingsRepository(createDataStore(this))

        repository.setForecastDays(999)

        assertEquals(SettingsDefaults.DEFAULT_FORECAST_DAYS, repository.forecastDays.first())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun setHistoricReference_updatesFlow() = runTest {
        val repository = DataStoreSettingsRepository(createDataStore(this))

        repository.setHistoricReference(HistoricReference.CURRENT)

        assertEquals(HistoricReference.CURRENT, repository.historicReference.first())
    }

    private fun createDataStore(scope: TestScope): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { tempFile() },
        )
    }

    private fun tempFile(): File = createTempFile("settings", ".preferences_pb").toFile()
}
