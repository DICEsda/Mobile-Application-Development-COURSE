package com.example.randomdog.domain-layer.usecase

import com.example.randomdog.data.repository.DogRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveIsFavouriteUseCase @Inject constructor(private val repository: DogRepository) {
    operator fun invoke(imageUrl: String): Flow<Boolean> = repository.observeIsFavourite(imageUrl)
}
