package com.example.randomdog.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

// ============================================================================
// Dependency Injection: Coroutine Dispatchers
// Why inject? Production uses IO pool; tests swap Unconfined for determinism
// ============================================================================

@Module  // Hilt module
@InstallIn(SingletonComponent::class)  // App-wide scope
object DispatcherModule {

    @Provides  // Provide a dependency
    @IoDispatcher  // Custom qualifier: distinguishes this from other dispatchers
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
    // ============================================================================
    // Provides: CoroutineDispatcher (thread pool for blocking I/O)
    // Dispatchers.IO: pre-built pool of threads for network/DB operations
    //
    // Where it's used:
    //   Repository.getRandomDog() = withContext(ioDispatcher) { ... }
    //   Repository.toggleFavourite() = withContext(ioDispatcher) { ... }
    //
    // Why inject instead of hardcoding?
    //   Production: @IoDispatcher → Dispatchers.IO (real thread pool)
    //   Tests: @IoDispatcher → Unconfined (instant execution, deterministic)
    //
    // Benefit: Tests run deterministically; no flaky race conditions
    // ============================================================================
}
