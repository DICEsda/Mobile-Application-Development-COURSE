package com.example.randomdog.data.repository

import com.example.randomdog.data.local.FavouriteDogDao
import com.example.randomdog.data.mapper.toDog
import com.example.randomdog.data.mapper.toEntity
import com.example.randomdog.data.remote.DogApi
import com.example.randomdog.di.IoDispatcher
import com.example.randomdog.domain.model.Dog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class DogRepositoryImpl @Inject constructor(
    private val api: DogApi,
    private val dao: FavouriteDogDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : DogRepository {

    override suspend fun getRandomDog(): Result<Dog> = withContext(ioDispatcher) {
        runCatching { api.getRandomDog().toDog() }
    }

    override fun observeFavourites(): Flow<List<Dog>> =
        dao.observeAll().map { entities -> entities.map { it.toDog() } }

    override fun observeIsFavourite(imageUrl: String): Flow<Boolean> =
        dao.observeIsFavourite(imageUrl)

    override suspend fun toggleFavourite(dog: Dog) = withContext(ioDispatcher) {
        if (dao.isFavourite(dog.imageUrl)) {
            dao.deleteByUrl(dog.imageUrl)
        } else {
            dao.insert(dog.toEntity(addedAt = System.currentTimeMillis()))
        }
    }
}
