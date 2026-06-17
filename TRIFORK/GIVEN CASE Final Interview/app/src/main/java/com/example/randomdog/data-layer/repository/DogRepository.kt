package com.example.randomdog.data.repository

import com.example.randomdog.domain-layer.model.Dog
import kotlinx.coroutines.flow.Flow

interface DogRepository {
    suspend fun getRandomDog(): Result<Dog>
    fun observeFavourites(): Flow<List<Dog>>
    fun observeIsFavourite(imageUrl: String): Flow<Boolean>
    suspend fun toggleFavourite(dog: Dog)
}
