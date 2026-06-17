package com.example.randomdog.data.mapper

import com.example.randomdog.data.local.FavouriteDogEntity
import com.example.randomdog.data.remote.dto.DogImageDto
import com.example.randomdog.domain-layer.model.Dog

// ============================================================================
// Mappers: Translation at layer boundaries (DTO ↔ Entity ↔ Domain)
// Decouples domain model from wire/DB shapes
// If API/DB change, only mappers change (not UI, not use cases)
// ============================================================================

fun DogImageDto.toDog(): Dog = Dog(
    imageUrl = message,  // DTO field: image URL from JSON
    breed = parseBreed(message)  // Extract breed from URL path
)
// Used: Repository.getRandomDog() after Retrofit call

fun FavouriteDogEntity.toDog(): Dog = Dog(
    imageUrl = imageUrl,  // Room entity
    breed = breed
)
// Used: Repository.observeFavourites().map { entities.map { it.toDog() } }

fun Dog.toEntity(addedAt: Long): FavouriteDogEntity =
    FavouriteDogEntity(imageUrl = imageUrl, breed = breed, addedAt = addedAt)
// Used: Repository.toggleFavourite() before dao.insert()
// Adds timestamp (for DB ordering by most recent)

/**
 * dog.ceo encodes breed in the path: .../breeds/<breed>[-<subbreed>]/<file>.jpg
 * "hound-afghan" → split → reverse → capitalize → "Afghan Hound"
 * Business logic example: shows how mappers handle domain-specific parsing
 */
internal fun parseBreed(url: String): String? {
    val slug = url.substringAfter("/breeds/", "")
        // Extract after "/breeds/": "hound-afghan/xyz.jpg"
        .substringBefore("/", "")
        // Extract before next "/": "hound-afghan"
        .takeIf { it.isNotBlank() } ?: return null
        // If empty, invalid URL → return null

    return slug.split("-")
        // Split: ["hound", "afghan"]
        .reversed()
        // Reverse: ["afghan", "hound"]
        .joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            // Capitalize: "hound" → "Hound", "afghan" → "Afghan"
        }
    // Result: "Afghan Hound"
}
