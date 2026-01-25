package com.example.gamecollection.viewmodel

import androidx.lifecycle.ViewModel
import com.example.gamecollection.data.model.Category
import com.example.gamecollection.data.model.Game
import com.example.gamecollection.data.repository.GameRepository

/**
 * ViewModel that provides data to the UI components.
 * Acts as an intermediary between the Repository and the UI.
 * Follows the separation of concerns principle.
 */
class GameViewModel : ViewModel() {

    /**
     * Returns all available categories for the main menu.
     */
    fun getCategories(): List<Category> {
        return GameRepository.getCategories()
    }

    /**
     * Returns a category by its ID.
     */
    fun getCategoryById(categoryId: String): Category? {
        return GameRepository.getCategoryById(categoryId)
    }

    /**
     * Returns all games in a specific category.
     */
    fun getGamesByCategory(categoryId: String): List<Game> {
        return GameRepository.getGamesByCategory(categoryId)
    }

    /**
     * Returns a specific game by its ID.
     */
    fun getGameById(gameId: String): Game? {
        return GameRepository.getGameById(gameId)
    }
}
