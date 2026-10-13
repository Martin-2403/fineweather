package com.example.fineweather.data.repositories

import com.example.fineweather.data.local.dao.GeoCodeDao
import com.example.fineweather.data.local.dao.WeatherDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

data class CacheStats(
    val weatherEntries: Int = 0,
    val searchEntries: Int = 0,
) {
    val isEmpty: Boolean get() = weatherEntries == 0 && searchEntries == 0
}

interface CacheRepository {
    val stats: Flow<CacheStats>

    suspend fun clear()
}

class RoomCacheRepository(
    private val weatherDao: WeatherDao,
    private val geoCodeDao: GeoCodeDao,
) : CacheRepository {

    override val stats: Flow<CacheStats> =
        combine(weatherDao.observeCount(), geoCodeDao.observeSearchCount()) { weather, searches ->
            CacheStats(weatherEntries = weather, searchEntries = searches)
        }.distinctUntilChanged()

    override suspend fun clear() {
        weatherDao.clearAll()
        geoCodeDao.clearAll()
    }
}
