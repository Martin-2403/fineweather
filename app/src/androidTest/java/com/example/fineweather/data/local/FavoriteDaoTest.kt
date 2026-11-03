package com.example.fineweather.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.fineweather.data.local.dao.FavoriteDao
import com.example.fineweather.data.local.entities.FavoritePlaceEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FavoriteDaoTest {

    private lateinit var db: WeatherDatabase
    private lateinit var dao: FavoriteDao

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db =
            Room
                .inMemoryDatabaseBuilder(context, WeatherDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = db.favoriteDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun ids() = dao.observeAll().first().map { it.id }

    private suspend fun threeFavorites() {
        dao.upsert(favorite("old", addedAt = 1))
        dao.upsert(favorite("mid", addedAt = 2))
        dao.upsert(favorite("new", addedAt = 3))
    }

    @Test
    fun withoutHome_listsNewestFirstAndHasNoHome() = runBlocking {
        threeFavorites()

        assertEquals(listOf("new", "mid", "old"), ids())
        assertNull(dao.getHome())
        assertNull(dao.observeHomeId().first())
    }

    @Test
    fun setHome_putsThePlaceFirstAndKeepsTheOthersByAddedAt() = runBlocking {
        threeFavorites()

        dao.setHome("old")

        assertEquals(listOf("old", "new", "mid"), ids())
        assertEquals("old", dao.getHome()?.id)
        assertEquals("old", dao.observeHomeId().first())
    }

    @Test
    fun setHome_movesTheHomeAndLeavesExactlyOne() = runBlocking {
        threeFavorites()
        dao.setHome("old")

        dao.setHome("mid")

        assertEquals(listOf("mid", "new", "old"), ids())
        assertEquals(listOf("mid"), dao.observeAll().first().filter { it.isHome }.map { it.id })
    }

    @Test
    fun setHome_forANonFavoriteChangesNothing() = runBlocking {
        threeFavorites()
        dao.setHome("mid")

        dao.setHome("unknown")

        assertEquals("mid", dao.getHome()?.id)
        assertEquals(3, dao.count())
    }

    @Test
    fun setHome_forANonFavoriteSetsNoHome() = runBlocking {
        threeFavorites()

        dao.setHome("unknown")

        assertNull(dao.getHome())
    }

    @Test
    fun clearHome_returnsThePlaceToItsAddedAtPosition() = runBlocking {
        threeFavorites()
        dao.setHome("old")

        dao.clearHome()

        assertEquals(listOf("new", "mid", "old"), ids())
        assertNull(dao.observeHomeId().first())
    }

    @Test
    fun removingTheHome_leavesNoHome() = runBlocking {
        threeFavorites()
        dao.setHome("mid")

        dao.deleteById("mid")

        assertNull(dao.getHome())
        assertEquals(listOf("new", "old"), ids())
    }

    @Test
    fun save_keepsTheHomeFlagOfAnExistingRow() = runBlocking {
        threeFavorites()
        dao.setHome("old")

        dao.save(favorite("old", addedAt = 10))
        dao.save(favorite("new", addedAt = 11))

        assertEquals("old", dao.getHome()?.id)
        assertEquals(listOf("old", "new", "mid"), ids())
        assertEquals(10L, dao.getHome()?.addedAt)
    }

    @Test
    fun save_storesANewPlaceWithoutHome() = runBlocking {
        dao.save(favorite("a", addedAt = 1))

        assertNull(dao.getHome())
        assertEquals(listOf("a"), ids())
    }

    private fun favorite(
        id: String,
        addedAt: Long,
    ) = FavoritePlaceEntity(
        id = id,
        name = "Place $id",
        country = null,
        admin1 = null,
        latitude = 0.0,
        longitude = 0.0,
        addedAt = addedAt,
    )
}
