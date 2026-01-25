package com.example.gamecollection.data.model

/**
 * Represents a video game in the collection.
 * Contains all details about a specific game.
 */
data class Game(
    val id: String,
    val title: String,
    val categoryId: String,
    val developer: String,
    val publisher: String,
    val releaseYear: Int,
    val genre: String,
    val platform: String,
    val rating: Float, // Rating out of 5
    val description: String,
    val imageUrl: String, // Placeholder for game artwork
    val features: List<String>,
    val minPlayers: Int,
    val maxPlayers: Int,
    val isMultiplayer: Boolean,
    val price: Double
)
