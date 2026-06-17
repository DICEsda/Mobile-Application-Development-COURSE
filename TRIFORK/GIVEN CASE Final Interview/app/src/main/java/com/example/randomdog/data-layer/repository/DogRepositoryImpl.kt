package com.example.randomdog.data.repository

import com.example.randomdog.data.local.FavouriteDogDao
import com.example.randomdog.data.mapper.toDog
import com.example.randomdog.data.mapper.toEntity
import com.example.randomdog.data.remote.DogApi
import com.example.randomdog.di.IoDispatcher
import com.example.randomdog.domain-layer.model.Dog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

// ============================================================================
// Data Layer: Orchestrates Retrofit (API) + Room (DB)
// Single source of truth: all data requests go through repository
// ============================================================================

class DogRepositoryImpl @Inject constructor(
    private val api: DogApi,  // Retrofit service (network)
    private val dao: FavouriteDogDao,  // Room DAO (local DB)
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,  // Injected thread pool (Dispatchers.IO)
) : DogRepository {  // Implements interface (testable: use cases depend on interface, not impl)

    override suspend fun getRandomDog(): Result<Dog> = withContext(ioDispatcher) {
        // Switch from Main → IO dispatcher (network call is blocking)
        // Caller (ViewModel on Main) pauses here, resumes on Main when done

        runCatching {
            // Try-catch wrapper: converts exception to Result (error is a value, never throws)
            api.getRandomDog()  // Retrofit suspend: hits dog.ceo API
                .toDog()  // Mapper: DTO → Dog (extracts breed from URL)
        }
        // Result.success(Dog) or Result.failure(e); returns to caller
    }

    override fun observeFavourites(): Flow<List<Dog>> =
        // Reactive: emit list whenever Room changes (INSERT/DELETE)
        dao.observeAll()  // Room Flow<List<Entity>> (cold, hot on emissions)
            .map { entities -> entities.map { it.toDog() } }
        // Transform: Entity → Dog (decouples Room shape from UI)
        // Returns: Flow<List<Dog>> for UI to collect

    override fun observeIsFavourite(imageUrl: String): Flow<Boolean> =
        // Reactive: emit true/false as row exists/vanishes
        dao.observeIsFavourite(imageUrl)  // Room Flow<Boolean>
        // Passed through as-is (already Boolean, no mapping needed)

    override suspend fun toggleFavourite(dog: Dog) = withContext(ioDispatcher) {
        // Add or remove dog from favourites (idempotent: toggle)
        // Switch from Main → IO dispatcher (DB write is blocking)

        if (dao.isFavourite(dog.imageUrl)) {
            // Suspend query: check if row exists (on IO dispatcher)
            dao.deleteByUrl(dog.imageUrl)  // Suspend delete (on IO dispatcher)
            // When done: Room emits new list via observeAll() Flow
        } else {
            // Row doesn't exist, add it
            dao.insert(dog.toEntity(addedAt = System.currentTimeMillis()))
            // Mapper: Dog → Entity (adds timestamp)
            // Suspend insert (on IO dispatcher)
            // When done: Room emits new list via observeAll() Flow
        }
        // FavouritesViewModel collects observeAll() → state updates → gallery recomposes
    }
}
