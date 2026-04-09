package com.example.fineweather.data.repositories

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.fineweather.data.models.GeoPlace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

interface FavoriteRepository {
    val favorite: Flow<GeoPlace?>
    suspend fun setFavorite(place: GeoPlace)
    suspend fun clearFavorite()
}

class DataStoreFavoriteRepository(
    private val dataStore: DataStore<Preferences>,
) : FavoriteRepository {

    private object Keys {
        val ID = stringPreferencesKey("favorite_id")
        val NAME = stringPreferencesKey("favorite_name")
        val COUNTRY = stringPreferencesKey("favorite_country")
        val ADMIN1 = stringPreferencesKey("favorite_admin1")
        val LATITUDE = doublePreferencesKey("favorite_latitude")
        val LONGITUDE = doublePreferencesKey("favorite_longitude")
    }

    override val favorite: Flow<GeoPlace?> =
        dataStore.data
            .map { prefs ->
                val id = prefs[Keys.ID] ?: return@map null
                val name = prefs[Keys.NAME] ?: return@map null
                val latitude = prefs[Keys.LATITUDE] ?: return@map null
                val longitude = prefs[Keys.LONGITUDE] ?: return@map null
                val country = prefs[Keys.COUNTRY]?.takeIf { it.isNotBlank() }
                val admin1 = prefs[Keys.ADMIN1]?.takeIf { it.isNotBlank() }
                GeoPlace(
                    id = id,
                    name = name,
                    country = country,
                    admin1 = admin1,
                    latitude = latitude,
                    longitude = longitude,
                )
            }
            .distinctUntilChanged()

    override suspend fun setFavorite(place: GeoPlace) {
        dataStore.edit { prefs ->
            prefs[Keys.ID] = place.id
            prefs[Keys.NAME] = place.name
            prefs[Keys.COUNTRY] = place.country.orEmpty()
            prefs[Keys.ADMIN1] = place.admin1.orEmpty()
            prefs[Keys.LATITUDE] = place.latitude
            prefs[Keys.LONGITUDE] = place.longitude
        }
    }

    override suspend fun clearFavorite() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.ID)
            prefs.remove(Keys.NAME)
            prefs.remove(Keys.COUNTRY)
            prefs.remove(Keys.ADMIN1)
            prefs.remove(Keys.LATITUDE)
            prefs.remove(Keys.LONGITUDE)
        }
    }
}
