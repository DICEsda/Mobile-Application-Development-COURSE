package com.example.randomdog.di

import com.example.randomdog.data.repository.DogRepository
import com.example.randomdog.data.repository.DogRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// ============================================================================
// Dependency Injection: Repository Binding
// Maps interface → implementation (use cases depend on interface, tests swap fake)
// ============================================================================

@Module  // Hilt module: declares how to create dependencies
@InstallIn(SingletonComponent::class)  // Scope: app-wide lifetime
abstract class RepositoryModule {

    @Binds  // Bind impl to interface (simpler than @Provides for impl → interface)
    @Singleton  // Single instance for entire app
    abstract fun bindDogRepository(impl: DogRepositoryImpl): DogRepository
    // ============================================================================
    // Maps: DogRepositoryImpl (concrete impl) → DogRepository (interface)
    //
    // When something needs DogRepository:
    //   Hilt sees: "DogRepositoryImpl provides DogRepository"
    //   Creates: DogRepositoryImpl(api, dao, ioDispatcher) [with deps resolved]
    //   Injects: the impl instance
    //
    // Why this matters:
    //   - Use cases depend on DogRepository (interface)
    //   - Tests can @Provide a FakeDogRepository without changing use cases
    //   - Prod uses real DogRepositoryImpl (Retrofit + Room)
    //   - Tests use FakeDogRepository (in-memory)
    // ============================================================================
}
