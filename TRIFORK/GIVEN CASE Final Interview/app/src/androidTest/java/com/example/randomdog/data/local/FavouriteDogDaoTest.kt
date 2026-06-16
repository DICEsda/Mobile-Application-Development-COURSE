package com.example.randomdog.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FavouriteDogDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: FavouriteDogDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.favouriteDogDao()
    }

    @After fun tearDown() = db.close()

    @Test
    fun insert_then_observe_returns_row() = runTest {
        val entity = FavouriteDogEntity("url-1", "Pug", 1L)
        dao.insert(entity)

        dao.observeAll().test {
            assertEquals(listOf(entity), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(dao.isFavourite("url-1"))
    }

    @Test
    fun delete_removes_row() = runTest {
        dao.insert(FavouriteDogEntity("url-1", "Pug", 1L))
        dao.deleteByUrl("url-1")
        assertFalse(dao.isFavourite("url-1"))
    }
}
