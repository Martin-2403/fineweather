package com.example.fineweather.data.repositories

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.fineweather.data.local.WeatherDatabase
import com.example.fineweather.data.models.GeoPlace
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomFavoriteRepositoryTest {

    private lateinit var db: WeatherDatabase
    private lateinit var repository: RoomFavoriteRepository

    private val augsburg = GeoPlace("1", "Augsburg", "Germany", "Bavaria", 48.37, 10.90)
    private val berlin = GeoPlace("2", "Berlin", "Germany", "Berlin", 52.52, 13.40)

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db =
            Room
                .inMemoryDatabaseBuilder(context, WeatherDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repository = RoomFavoriteRepository(db.favoriteDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun setHome_exposesTheHomeIdAndPlacesItFirst() = runBlocking {
        repository.addFavorite(augsburg)
        repository.addFavorite(berlin)
        assertNull(repository.getHome())
        assertNull(repository.homeId.first())

        repository.setHome(augsburg.id)

        assertEquals(augsburg.id, repository.homeId.first())
        assertEquals(augsburg, repository.getHome())
        assertEquals(listOf(augsburg, berlin), repository.favorites.first())
    }

    @Test
    fun setHome_forANonFavoriteDoesNotCreateOne() = runBlocking {
        repository.addFavorite(augsburg)

        repository.setHome(berlin.id)

        assertNull(repository.getHome())
        assertEquals(listOf(augsburg), repository.favorites.first())
    }

    @Test
    fun clearHome_leavesNoHome() = runBlocking {
        repository.addFavorite(augsburg)
        repository.setHome(augsburg.id)

        repository.clearHome()

        assertNull(repository.homeId.first())
        assertNull(repository.getHome())
    }

    @Test
    fun removingTheHome_leavesNoHome() = runBlocking {
        repository.addFavorite(augsburg)
        repository.setHome(augsburg.id)

        repository.removeFavorite(augsburg.id)

        assertNull(repository.homeId.first())
        assertEquals(emptyList<GeoPlace>(), repository.favorites.first())
    }

    @Test
    fun addingTheHomeAgain_keepsItHome() = runBlocking {
        repository.addFavorite(augsburg)
        repository.setHome(augsburg.id)

        val result = repository.addFavorite(augsburg)

        assertEquals(FavoriteAddResult.ADDED, result)
        assertEquals(augsburg.id, repository.homeId.first())
    }

    @Test
    fun theLimitStillRefusesAnEleventhFavoriteButNotARepeatedOne() = runBlocking {
        val places = (1..RoomFavoriteRepository.MAX_FAVORITES).map { GeoPlace("id-$it", "City $it", null, null, 1.0, 2.0) }
        places.forEach { repository.addFavorite(it) }
        repository.setHome("id-3")

        assertEquals(FavoriteAddResult.LIMIT_REACHED, repository.addFavorite(GeoPlace("extra", "Extra", null, null, 1.0, 2.0)))
        assertEquals(FavoriteAddResult.ADDED, repository.addFavorite(places[2]))
        assertEquals("id-3", repository.homeId.first())
    }
}
