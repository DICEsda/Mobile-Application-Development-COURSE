package com.example.randomdog.domain.usecase

import com.example.randomdog.data.repository.DogRepository
import com.example.randomdog.domain.model.Dog
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.coEvery
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UseCasesTest {

    private val repo = mockk<DogRepository>(relaxed = true)
    private val dog = Dog("https://images.dog.ceo/breeds/pug/x.jpg", "Pug")

    @Test
    fun `GetRandomDog delegates to repository`() = runTest {
        coEvery { repo.getRandomDog() } returns Result.success(dog)
        val result = GetRandomDogUseCase(repo)()
        assertTrue(result.isSuccess)
        assertEquals(dog, result.getOrNull())
    }

    @Test
    fun `ToggleFavourite delegates to repository`() = runTest {
        ToggleFavouriteUseCase(repo)(dog)
        coVerify { repo.toggleFavourite(dog) }
    }

    @Test
    fun `GetFavouriteDogs delegates to repository`() = runTest {
        every { repo.observeFavourites() } returns flowOf(listOf(dog))
        val emitted = mutableListOf<List<Dog>>()
        GetFavouriteDogsUseCase(repo)().collect { emitted.add(it) }
        assertEquals(listOf(listOf(dog)), emitted)
    }

    @Test
    fun `ObserveIsFavourite delegates to repository`() = runTest {
        every { repo.observeIsFavourite(dog.imageUrl) } returns flowOf(true)
        val emitted = mutableListOf<Boolean>()
        ObserveIsFavouriteUseCase(repo)(dog.imageUrl).collect { emitted.add(it) }
        assertEquals(listOf(true), emitted)
    }
}
