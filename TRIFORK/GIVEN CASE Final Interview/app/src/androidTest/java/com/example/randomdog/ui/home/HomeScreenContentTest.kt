package com.example.randomdog.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.randomdog.domain.model.Dog
import com.example.randomdog.ui.theme.RandomDogTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeScreenContentTest {

    @get:Rule
    val rule = createComposeRule()

    private val dog = Dog("https://images.dog.ceo/breeds/pug/x.jpg", "Pug")

    @Test
    fun loadingState_showsNoActionButton() {
        rule.setContent {
            RandomDogTheme {
                HomeScreenContent(
                    state = HomeUiState(isLoading = true),
                    onNewDog = {}, onToggleFavourite = {}, onRetry = {},
                )
            }
        }
        rule.onNodeWithText("New dog").assertDoesNotExist()
    }

    @Test
    fun errorState_showsMessageAndRetryFiresCallback() {
        var retried = false
        rule.setContent {
            RandomDogTheme {
                HomeScreenContent(
                    state = HomeUiState(errorMessage = "Couldn't fetch a dog."),
                    onNewDog = {}, onToggleFavourite = {}, onRetry = { retried = true },
                )
            }
        }
        rule.onNodeWithText("Couldn't fetch a dog.").assertIsDisplayed()
        rule.onNodeWithText("Retry").performClick()
        assertTrue(retried)
    }

    @Test
    fun contentState_showsDogAndButtons_fireCallbacks() {
        var newDog = false
        var toggled = false
        rule.setContent {
            RandomDogTheme {
                HomeScreenContent(
                    state = HomeUiState(dog = dog, isFavourite = false),
                    onNewDog = { newDog = true },
                    onToggleFavourite = { toggled = true },
                    onRetry = {},
                )
            }
        }
        rule.onNodeWithText("Pug").assertIsDisplayed()
        rule.onNodeWithContentDescription("Add to favourites").performClick()
        assertTrue(toggled)
        rule.onNodeWithText("New dog").performClick()
        assertTrue(newDog)
    }

    @Test
    fun favouritedDog_showsFilledHeart() {
        rule.setContent {
            RandomDogTheme {
                HomeScreenContent(
                    state = HomeUiState(dog = dog, isFavourite = true),
                    onNewDog = {}, onToggleFavourite = {}, onRetry = {},
                )
            }
        }
        rule.onNodeWithContentDescription("Remove from favourites").assertIsDisplayed()
    }
}
