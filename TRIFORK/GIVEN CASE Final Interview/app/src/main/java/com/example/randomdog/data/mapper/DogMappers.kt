package com.example.randomdog.data.mapper

import com.example.randomdog.data.local.FavouriteDogEntity
import com.example.randomdog.data.remote.dto.DogImageDto
import com.example.randomdog.domain.model.Dog

fun DogImageDto.toDog(): Dog = Dog(imageUrl = message, breed = parseBreed(message))

fun FavouriteDogEntity.toDog(): Dog = Dog(imageUrl = imageUrl, breed = breed)

fun Dog.toEntity(addedAt: Long): FavouriteDogEntity =
    FavouriteDogEntity(imageUrl = imageUrl, breed = breed, addedAt = addedAt)

/**
 * dog.ceo encodes breed in the path: .../breeds/<breed>[-<subbreed>]/<file>.jpg
 * "hound-afghan" -> reversed words -> "Afghan Hound". Returns null if not parseable.
 */
internal fun parseBreed(url: String): String? {
    val slug = url.substringAfter("/breeds/", "")
        .substringBefore("/", "")
        .takeIf { it.isNotBlank() } ?: return null
    return slug.split("-")
        .reversed()
        .joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
}
