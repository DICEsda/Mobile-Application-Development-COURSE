package com.example.randomdog.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favourites")
data class FavouriteDogEntity(
    @PrimaryKey val imageUrl: String,
    val breed: String?,
    val addedAt: Long,
)
