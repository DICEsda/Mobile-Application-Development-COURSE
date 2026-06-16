package com.example.randomdog.domain.usecase

import com.example.randomdog.data.repository.DogRepository
import com.example.randomdog.domain.model.Dog
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetFavouriteDogsUseCase @Inject constructor(private val repository: DogRepository) {
    operator fun invoke(): Flow<List<Dog>> = repository.observeFavourites()
}
