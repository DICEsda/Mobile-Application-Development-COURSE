package com.example.randomdog.domain-layer.model

// ============================================================================
// Domain Model: Framework-agnostic, pure data
// NOT a DTO (@Serializable), NOT a Room entity (@Entity)
// Decoupled so API/DB shapes can change without touching UI
// ============================================================================

data class Dog(
    val imageUrl: String,  // Remote URL (not cached)
    val breed: String?,  // Parsed from URL by mapper
)
// Used by: UI layer, domain layer (use cases), repository interface
// Mappers translate: DogImageDto (DTO) → Dog, FavouriteDogEntity (Entity) → Dog
