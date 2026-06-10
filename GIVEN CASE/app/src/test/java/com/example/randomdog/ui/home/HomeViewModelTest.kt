package com.example.randomdog.ui.home

import app.cash.turbine.test
import com.example.randomdog.data.repository.DogRepository
import com.example.randomdog.domain.model.Dog
import com.example.randomdog.domain.usecase.GetRandomDogUseCase
import com.example.randomdog.domain.usecase.ObserveIsFavouriteUseCase
import com.example.randomdog.domain.usecase.ToggleFavouriteUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repo = mockk<DogRepository>(relaxed = true)
    private val dog = Dog("https://images.dog.ceo/breeds/pug/x.jpg", "Pug")

    private fun viewModel() = HomeViewModel(
        getRandomDog = GetRandomDogUseCase(repo),
        toggleFavourite = ToggleFavouriteUseCase(repo),
        observeIsFavourite = ObserveIsFavouriteUseCase(repo),
    )

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `loads a dog on init and exposes Success`() = runTest(dispatcher) {
        coEvery { repo.getRandomDog() } returns Result.success(dog)
        every { repo.observeIsFavourite(dog.imageUrl) } returns flowOf(false)

        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertEquals(dog, state.dog)
        assertNull(state.errorMessage)
    }

    @Test
    fun `network failure produces error message`() = runTest(dispatcher) {
        coEvery { repo.getRandomDog() } returns Result.failure(RuntimeException("boom"))

        val vm = viewModel()
        advanceUntilIdle()

        assertFalse(vm.state.value.isLoading)
        assertNull(vm.state.value.dog)
        assertTrue(vm.state.value.errorMessage != null)
    }

    @Test
    fun `isFavourite flag tracks repository flow`() = runTest(dispatcher) {
        coEvery { repo.getRandomDog() } returns Result.success(dog)
        every { repo.observeIsFavourite(dog.imageUrl) } returns flowOf(true)

        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.state.value.isFavourite)
    }

    @Test
    fun `network failure emits ShowMessage effect`() = runTest(dispatcher) {
        coEvery { repo.getRandomDog() } returns Result.failure(RuntimeException("boom"))
        val vm = viewModel()
        advanceUntilIdle()
        vm.effects.test {
            assertTrue(awaitItem() is HomeEffect.ShowMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ToggleFavourite intent calls use case`() = runTest(dispatcher) {
        coEvery { repo.getRandomDog() } returns Result.success(dog)
        every { repo.observeIsFavourite(dog.imageUrl) } returns flowOf(false)

        val vm = viewModel()
        advanceUntilIdle()
        vm.onIntent(HomeIntent.ToggleFavourite)
        advanceUntilIdle()

        coVerify { repo.toggleFavourite(dog) }
    }
}
