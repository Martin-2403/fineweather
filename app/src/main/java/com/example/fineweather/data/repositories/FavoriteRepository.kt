package com.example.fineweather.data.repositories

import com.example.fineweather.data.models.GeoPlace
import com.example.fineweather.data.local.doa.FavoriteDao
import com.example.fineweather.data.local.entities.FavoritePlaceEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

interface FavoriteRepository {
    val favorites: Flow<List<GeoPlace>>
    suspend fun addFavorite(place: GeoPlace): FavoriteAddResult
    suspend fun removeFavorite(placeId: String)
}

enum class FavoriteAddResult {
    ADDED,
    LIMIT_REACHED,
}

class RoomFavoriteRepository(
    private val favoriteDao: FavoriteDao,
) : FavoriteRepository {

    override val favorites: Flow<List<GeoPlace>> =
        favoriteDao.observeAll()
            .map { entities -> entities.map { it.toGeoPlace() } }
            .distinctUntilChanged()

    override suspend fun addFavorite(place: GeoPlace): FavoriteAddResult {
        val alreadyFavorite = favoriteDao.exists(place.id)
        if (!alreadyFavorite) {
            val count = favoriteDao.count()
            if (count >= MAX_FAVORITES) {
                return FavoriteAddResult.LIMIT_REACHED
            }
        }
        favoriteDao.upsert(place.toEntity())
        return FavoriteAddResult.ADDED
    }

    override suspend fun removeFavorite(placeId: String) {
        favoriteDao.deleteById(placeId)
    }

    private fun GeoPlace.toEntity(): FavoritePlaceEntity =
        FavoritePlaceEntity(
            id = id,
            name = name,
            country = country,
            admin1 = admin1,
            latitude = latitude,
            longitude = longitude,
            addedAt = System.currentTimeMillis(),
        )

    private fun FavoritePlaceEntity.toGeoPlace(): GeoPlace =
        GeoPlace(
            id = id,
            name = name,
            country = country,
            admin1 = admin1,
            latitude = latitude,
            longitude = longitude,
        )

    companion object {
        const val MAX_FAVORITES = 10
    }
}
