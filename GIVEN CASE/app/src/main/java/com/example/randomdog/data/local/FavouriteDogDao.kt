package com.example.randomdog.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavouriteDogDao {

    @Query("SELECT * FROM favourites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavouriteDogEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favourites WHERE imageUrl = :url)")
    fun observeIsFavourite(url: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favourites WHERE imageUrl = :url)")
    suspend fun isFavourite(url: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(dog: FavouriteDogEntity)

    @Query("DELETE FROM favourites WHERE imageUrl = :url")
    suspend fun deleteByUrl(url: String)
}
