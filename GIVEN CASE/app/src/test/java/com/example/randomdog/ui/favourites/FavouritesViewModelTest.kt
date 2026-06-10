package com.example.randomdog.ui.favourites

import com.example.randomdog.data.repository.DogRepository
import com.example.randomdog.domain.model.Dog
import com.example.randomdog.domain.usecase.GetFavouriteDogsUseCase
import com.example.randomdog.domain.usecase.ToggleFavouriteUseCase
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
import org.junit.Before
import org.junit.Test

class FavouritesViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repo = mockk<DogRepository>(relaxed = true)
    private val dog = Dog("https://images.dog.ceo/breeds/pug/x.jpg", "Pug")

    private fun viewModel() = FavouritesViewModel(
        getFavourites = GetFavouriteDogsUseCase(repo),
        toggleFavourite = ToggleFavouriteUseCase(repo),
    )

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `exposes favourites from repository`() = runTest(dispatcher) {
        every { repo.observeFavourites() } returns flowOf(listOf(dog))

        val vm = viewModel()
        advanceUntilIdle()

        assertFalse(vm.state.value.isLoading)
        assertEquals(listOf(dog), vm.state.value.favourites)
    }

    @Test
    fun `RemoveFavourite intent toggles via use case`() = runTest(dispatcher) {
        every { repo.observeFavourites() } returns flowOf(listOf(dog))

        val vm = viewModel()
        advanceUntilIdle()
        vm.onIntent(FavouritesIntent.RemoveFavourite(dog))
        advanceUntilIdle()

        coVerify { repo.toggleFavourite(dog) }
    }
}
