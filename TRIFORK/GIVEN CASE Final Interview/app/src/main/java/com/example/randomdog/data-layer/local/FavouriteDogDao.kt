package com.example.randomdog.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// ============================================================================
// Room DAO: Data Access Object — typed queries into SQLite
// ============================================================================

@Dao  // Marks this as a Room data access interface
interface FavouriteDogDao {

    @Query("SELECT * FROM favourites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavouriteDogEntity>>
    // ========== Reactive query ==========
    // Returns: Flow<List<Entity>> (cold, hot on emissions)
    // Behavior: emits whenever Room detects INSERT/DELETE
    // Usage: FavouritesViewModel collects → state updates → gallery recomposes
    // No manual refresh needed!

    @Query("SELECT EXISTS(SELECT 1 FROM favourites WHERE imageUrl = :url)")
    fun observeIsFavourite(url: String): Flow<Boolean>
    // ========== Reactive boolean query ==========
    // Returns: Flow<Boolean>
    // Behavior: emits true when row exists, false when deleted
    // Usage: HomeViewModel collects → state updates → heart icon (filled/outline) follows

    @Query("SELECT EXISTS(SELECT 1 FROM favourites WHERE imageUrl = :url)")
    suspend fun isFavourite(url: String): Boolean
    // ========== Non-reactive check ==========
    // Returns: Boolean (not Flow)
    // Behavior: one-time query, doesn't emit on DB changes
    // Usage: Repository.toggleFavourite() checks before insert/delete

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(dog: FavouriteDogEntity)
    // suspend: off-thread write; called from viewModelScope.launch
    // onConflict=IGNORE: if URL exists, silently do nothing (idempotent)
    // Side effect: Room emits new list via observeAll() Flow

    @Query("DELETE FROM favourites WHERE imageUrl = :url")
    suspend fun deleteByUrl(url: String)
    // suspend: off-thread delete; called from viewModelScope.launch
    // Side effect: Room emits new list via observeAll() Flow
}
