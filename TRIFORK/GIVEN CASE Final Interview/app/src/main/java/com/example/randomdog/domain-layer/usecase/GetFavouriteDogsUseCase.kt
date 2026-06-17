package com.example.randomdog.domain-layer.usecase

import com.example.randomdog.data.repository.DogRepository
import com.example.randomdog.domain-layer.model.Dog
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetFavouriteDogsUseCase @Inject constructor(private val repository: DogRepository) {
    operator fun invoke(): Flow<List<Dog>> = repository.observeFavourites()
}
