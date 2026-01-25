package com.example.gamecollection.data.model

/**
 * Represents a game category in the collection.
 * Each category groups related games together.
 */
data class Category(
    val id: String,
    val name: String,
    val description: String,
    val iconName: String // Material icon name for the category
)
