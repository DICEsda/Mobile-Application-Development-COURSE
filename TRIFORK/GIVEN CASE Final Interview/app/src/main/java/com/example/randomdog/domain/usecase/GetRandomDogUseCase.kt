package com.example.randomdog.domain.usecase

import com.example.randomdog.data.repository.DogRepository
import com.example.randomdog.domain.model.Dog
import javax.inject.Inject

class GetRandomDogUseCase @Inject constructor(private val repository: DogRepository) {
    suspend operator fun invoke(): Result<Dog> = repository.getRandomDog()
}
