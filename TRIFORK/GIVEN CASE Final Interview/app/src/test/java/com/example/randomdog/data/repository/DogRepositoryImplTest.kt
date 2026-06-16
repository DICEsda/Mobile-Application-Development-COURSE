package com.example.randomdog.data.repository

import app.cash.turbine.test
import com.example.randomdog.data.local.FavouriteDogDao
import com.example.randomdog.data.local.FavouriteDogEntity
import com.example.randomdog.data.remote.DogApi
import com.example.randomdog.data.remote.dto.DogImageDto
import com.example.randomdog.domain.model.Dog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

private class FakeDogApi(
    var result: () -> DogImageDto = { DogImageDto("https://images.dog.ceo/breeds/pug/x.jpg", "success") },
) : DogApi {
    override suspend fun getRandomDog(): DogImageDto = result()
}

private class FakeFavouriteDao : FavouriteDogDao {
    val items = MutableStateFlow<List<FavouriteDogEntity>>(emptyList())
    override fun observeAll(): Flow<List<FavouriteDogEntity>> = items
    override fun observeIsFavourite(url: String): Flow<Boolean> =
        items.map { list -> list.any { it.imageUrl == url } }
    override suspend fun isFavourite(url: String): Boolean = items.value.any { it.imageUrl == url }
    override suspend fun insert(dog: FavouriteDogEntity) {
        items.value = (items.value.filterNot { it.imageUrl == dog.imageUrl } + dog)
    }
    override suspend fun deleteByUrl(url: String) {
        items.value = items.value.filterNot { it.imageUrl == url }
    }
}

class DogRepositoryImplTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private fun repo(api: DogApi = FakeDogApi(), dao: FakeFavouriteDao = FakeFavouriteDao()) =
        DogRepositoryImpl(api, dao, dispatcher)

    @Test
    fun `getRandomDog success maps dto to Dog`() = runTest(dispatcher) {
        val result = repo().getRandomDog()
        assertTrue(result.isSuccess)
        assertEquals("Pug", result.getOrNull()?.breed)
    }

    @Test
    fun `getRandomDog wraps network error in failure`() = runTest(dispatcher) {
        val api = FakeDogApi(result = { throw IOException("offline") })
        val result = repo(api = api).getRandomDog()
        assertTrue(result.isFailure)
    }

    @Test
    fun `toggleFavourite adds then removes`() = runTest(dispatcher) {
        val dao = FakeFavouriteDao()
        val r = repo(dao = dao)
        val dog = Dog("https://images.dog.ceo/breeds/pug/x.jpg", "Pug")

        r.toggleFavourite(dog)
        assertEquals(1, dao.items.value.size)

        r.toggleFavourite(dog)
        assertEquals(0, dao.items.value.size)
    }

    @Test
    fun `observeFavourites emits mapped domain models`() = runTest(dispatcher) {
        val dao = FakeFavouriteDao()
        val r = repo(dao = dao)
        r.toggleFavourite(Dog("https://images.dog.ceo/breeds/pug/x.jpg", "Pug"))

        r.observeFavourites().test {
            val first = awaitItem()
            assertEquals(1, first.size)
            assertEquals("Pug", first.first().breed)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
