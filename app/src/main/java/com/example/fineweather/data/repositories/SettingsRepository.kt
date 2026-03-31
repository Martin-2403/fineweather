package com.example.fineweather.data.repositories

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.fineweather.data.models.HistoricReference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.Locale

object SettingsDefaults {
    const val FORECAST_DAYS_SHORT = 7
    const val FORECAST_DAYS_LONG = 14
    const val DEFAULT_FORECAST_DAYS = FORECAST_DAYS_SHORT
    val DEFAULT_HISTORIC_REFERENCE: HistoricReference = HistoricReference.CLASSIC
    const val DEFAULT_SEARCH_LANGUAGE = "en"

    data class SearchLanguageOption(
        val code: String,
        val label: String,
    ) {
        fun displayLabel(): String = "$label ($code)"
    }

    val SEARCH_LANGUAGE_OPTIONS: List<SearchLanguageOption> =
        listOf(
            SearchLanguageOption("en", "English"),
            SearchLanguageOption("de", "German"),
            SearchLanguageOption("fr", "French"),
            SearchLanguageOption("es", "Spanish"),
            SearchLanguageOption("it", "Italian"),
            SearchLanguageOption("pt", "Portuguese"),
            SearchLanguageOption("ru", "Russian"),
            SearchLanguageOption("tr", "Turkish"),
            SearchLanguageOption("hi", "Hindi"),
        )

    val SEARCH_LANGUAGE_CODES: Set<String> =
        SEARCH_LANGUAGE_OPTIONS.map { it.code }.toSet()

    fun normalizeSearchLanguage(language: String): String {
        val normalized = language.trim().lowercase(Locale.ROOT)
        if (normalized.isBlank()) {
            return DEFAULT_SEARCH_LANGUAGE
        }
        return if (SEARCH_LANGUAGE_CODES.contains(normalized)) {
            normalized
        } else {
            DEFAULT_SEARCH_LANGUAGE
        }
    }
}

interface SettingsRepository {
    val forecastDays: Flow<Int>
    val historicReference: Flow<HistoricReference>
    val searchLanguage: Flow<String>
    suspend fun setForecastDays(days: Int)
    suspend fun setHistoricReference(reference: HistoricReference)
    suspend fun setSearchLanguage(language: String)
}

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    private object Keys {
        val FORECAST_DAYS = intPreferencesKey("forecast_days")
        val HISTORIC_REFERENCE = stringPreferencesKey("historic_reference")
        val SEARCH_LANGUAGE = stringPreferencesKey("search_language")
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

    override val historicReference: Flow<HistoricReference> =
        dataStore.data
            .map { prefs -> HistoricReference.fromId(prefs[Keys.HISTORIC_REFERENCE]) }
            .distinctUntilChanged()

    override val searchLanguage: Flow<String> =
        dataStore.data
            .map { prefs -> prefs[Keys.SEARCH_LANGUAGE] ?: SettingsDefaults.DEFAULT_SEARCH_LANGUAGE }
            .map { stored -> SettingsDefaults.normalizeSearchLanguage(stored) }
            .distinctUntilChanged()

    override suspend fun setForecastDays(days: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.FORECAST_DAYS] = days
        }
    }

    override suspend fun setHistoricReference(reference: HistoricReference) {
        dataStore.edit { prefs ->
            prefs[Keys.HISTORIC_REFERENCE] = reference.id
        }
    }

    override suspend fun setSearchLanguage(language: String) {
        dataStore.edit { prefs ->
            prefs[Keys.SEARCH_LANGUAGE] = SettingsDefaults.normalizeSearchLanguage(language)
        }
    }

}
