package com.example.fineweather.data.repositories

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

object SettingsDefaults {
    const val FORECAST_DAYS_SHORT = 7
    const val FORECAST_DAYS_LONG = 14
    const val DEFAULT_FORECAST_DAYS = FORECAST_DAYS_SHORT
}

interface SettingsRepository {
    val forecastDays: Flow<Int>
    suspend fun setForecastDays(days: Int)
}

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    private object Keys {
        val FORECAST_DAYS = intPreferencesKey("forecast_days")
    }

    override val forecastDays: Flow<Int> =
        dataStore.data
            .map { prefs -> prefs[Keys.FORECAST_DAYS] ?: SettingsDefaults.DEFAULT_FORECAST_DAYS }
            .map { stored ->
                if (stored == SettingsDefaults.FORECAST_DAYS_SHORT ||
                    stored == SettingsDefaults.FORECAST_DAYS_LONG
                ) {
                    stored
                } else {
                    SettingsDefaults.DEFAULT_FORECAST_DAYS
                }
            }
            .distinctUntilChanged()

    override suspend fun setForecastDays(days: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.FORECAST_DAYS] = days
        }
    }

}
