package com.example.randomdog.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [FavouriteDogEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favouriteDogDao(): FavouriteDogDao

    companion object {
        const val NAME = "random_dog.db"
    }
}
