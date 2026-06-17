package com.example.randomdog.domain-layer.usecase

import com.example.randomdog.data.repository.DogRepository
import com.example.randomdog.domain-layer.model.Dog
import javax.inject.Inject

class ToggleFavouriteUseCase @Inject constructor(private val repository: DogRepository) {
    suspend operator fun invoke(dog: Dog) = repository.toggleFavourite(dog)
}
