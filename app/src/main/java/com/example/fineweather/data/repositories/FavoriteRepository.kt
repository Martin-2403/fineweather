package com.example.fineweather.data.repositories

import com.example.fineweather.data.models.GeoPlace
import com.example.fineweather.data.local.dao.FavoriteDao
import com.example.fineweather.data.local.entities.FavoritePlaceEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

interface FavoriteRepository {
    /** The favorites, the home place first and the others newest first. */
    val favorites: Flow<List<GeoPlace>>

    /** The id of the home place, null while there is none. */
    val homeId: Flow<String?>
    suspend fun addFavorite(place: GeoPlace): FavoriteAddResult
    suspend fun removeFavorite(placeId: String)

    /** Makes the favorite [placeId] the home place, replacing the current one; does nothing for a non-favorite. */
    suspend fun setHome(placeId: String)
    suspend fun clearHome()

    /** The home place, read once; [favorites] needs a moment to emit after the start. */
    suspend fun getHome(): GeoPlace?
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

    override val homeId: Flow<String?> = favoriteDao.observeHomeId().distinctUntilChanged()

    override suspend fun addFavorite(place: GeoPlace): FavoriteAddResult {
        val alreadyFavorite = favoriteDao.exists(place.id)
        if (!alreadyFavorite) {
            val count = favoriteDao.count()
            if (count >= MAX_FAVORITES) {
                return FavoriteAddResult.LIMIT_REACHED
            }
        }
        favoriteDao.save(place.toEntity())
        return FavoriteAddResult.ADDED
    }

    override suspend fun removeFavorite(placeId: String) {
        favoriteDao.deleteById(placeId)
    }

    override suspend fun setHome(placeId: String) {
        favoriteDao.setHome(placeId)
    }

    override suspend fun clearHome() {
        favoriteDao.clearHome()
    }

    override suspend fun getHome(): GeoPlace? = favoriteDao.getHome()?.toGeoPlace()

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
