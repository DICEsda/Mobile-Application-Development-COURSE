package com.example.randomdog.ui.favourites

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.randomdog.domain.model.Dog
import com.example.randomdog.ui.theme.RandomDogTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FavouritesScreenContentTest {

    @get:Rule
    val rule = createComposeRule()

    private val pug = Dog("https://images.dog.ceo/breeds/pug/x.jpg", "Pug")
    private val lab = Dog("https://images.dog.ceo/breeds/labrador/y.jpg", "Labrador")

    @Test
    fun emptyState_showsHint() {
        rule.setContent {
            RandomDogTheme {
                FavouritesScreenContent(
                    state = FavouritesUiState(isLoading = false, favourites = emptyList()),
                    onRemove = {},
                )
            }
        }
        rule.onNodeWithText("No favourites yet — tap the heart on a dog.").assertIsDisplayed()
    }

    @Test
    fun content_showsItems_andRemoveFiresCallbackWithDog() {
        var removed: Dog? = null
        rule.setContent {
            RandomDogTheme {
                FavouritesScreenContent(
                    state = FavouritesUiState(isLoading = false, favourites = listOf(pug, lab)),
                    onRemove = { removed = it },
                )
            }
        }
        rule.onNodeWithContentDescription("Pug").assertIsDisplayed()
        rule.onAllNodesWithContentDescription("Remove from favourites").onFirst().performClick()
        assertEquals(pug, removed)
    }
}
