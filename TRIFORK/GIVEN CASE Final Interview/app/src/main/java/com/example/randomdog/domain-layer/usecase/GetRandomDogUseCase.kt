package com.example.randomdog.domain-layer.usecase

import com.example.randomdog.data.repository.DogRepository
import com.example.randomdog.domain-layer.model.Dog
import javax.inject.Inject

// ============================================================================
// Use Case: Business action (thin delegate pattern)
// Names the app's action, injectable for testing, decouples ViewModel from repository
// ============================================================================

class GetRandomDogUseCase @Inject constructor(private val repository: DogRepository) {
    suspend operator fun invoke(): Result<Dog> = repository.getRandomDog()
    // invoke(): operator allows call syntax: getRandomDog() instead of getRandomDog.invoke()
    // suspend: pauseable function (can be awaited in viewModelScope.launch)
    // Result<Dog>: error wrapped as value (never throws)
    // Thin delegate: just wraps repository call (testable in isolation)
}
