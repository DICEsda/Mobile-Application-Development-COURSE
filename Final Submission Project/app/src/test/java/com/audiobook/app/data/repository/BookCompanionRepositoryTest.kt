package com.audiobook.app.data.repository

import com.audiobook.app.data.model.Audiobook
import com.audiobook.app.data.model.Chapter
import com.audiobook.app.data.remote.llm.ChatMessage
import com.audiobook.app.data.remote.llm.ChatRole
import com.audiobook.app.data.remote.llm.LlmProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the pure prompt-construction logic in [BookCompanionRepository].
 * The grounding/wire builders are the architecturally interesting seam — they
 * encode how book context reaches the model — so they're tested in isolation
 * with a fake provider, no network or real LLM involved.
 */
class BookCompanionRepositoryTest {

    /** Captures the messages it's asked to complete; returns a canned reply. */
    private class FakeLlmProvider : LlmProvider {
        var lastMessages: List<ChatMessage>? = null
        override val displayName = "Fake"
        override suspend fun chat(messages: List<ChatMessage>, temperature: Float): Result<String> {
            lastMessages = messages
            return Result.success("canned reply")
        }
        override suspend fun listModels(): Result<List<String>> = Result.success(listOf("fake-model"))
    }

    private val provider = FakeLlmProvider()
    private val repo = BookCompanionRepository(provider)

    private fun book() = Audiobook(
        id = "b1",
        title = "Atomic Habits",
        author = "James Clear",
        coverUrl = "",
        duration = "5h",
        totalDurationMinutes = 300,
        currentChapter = 2,
        chapters = listOf(
            Chapter(number = 1, title = "The Fundamentals"),
            Chapter(number = 2, title = "Make It Obvious"),
            Chapter(number = 3, title = "Make It Attractive")
        ),
        description = "Tiny changes, remarkable results.",
        narrator = "James Clear"
    )

    @Test
    fun `grounding preamble includes the key book context`() {
        val preamble = repo.buildGroundingPreamble(book())

        assertTrue(preamble.contains("Title: Atomic Habits"))
        assertTrue(preamble.contains("Author: James Clear"))
        assertTrue(preamble.contains("Narrator: James Clear"))
        assertTrue(preamble.contains("Tiny changes, remarkable results."))
        assertTrue(preamble.contains("1. The Fundamentals"))
        assertTrue(preamble.contains("2. Make It Obvious"))
        assertTrue(preamble.contains("Chapter 2 of 3"))
    }

    @Test
    fun `missing description falls back to a placeholder`() {
        val preamble = repo.buildGroundingPreamble(book().copy(description = null))
        assertTrue(preamble.contains("(no description available)"))
    }

    @Test
    fun `first turn merges grounding into a single user message`() {
        val wire = repo.buildWireMessages(book(), history = emptyList(), question = "What's the core idea?")

        assertEquals(1, wire.size)
        assertEquals(ChatRole.USER, wire[0].role)
        assertTrue(wire[0].content.contains("Title: Atomic Habits")) // grounding present
        assertTrue(wire[0].content.contains("QUESTION:"))
        assertTrue(wire[0].content.contains("What's the core idea?"))
    }

    @Test
    fun `follow-up turns ground only the first message and keep alternation`() {
        val history = listOf(
            ChatMessage.user("What's the core idea?"),
            ChatMessage.assistant("Small habits compound.")
        )
        val wire = repo.buildWireMessages(book(), history, question = "Tell me more about chapter 2.")

        assertEquals(3, wire.size)
        // Grounding merged into the first user turn...
        assertTrue(wire[0].content.contains("Title: Atomic Habits"))
        assertTrue(wire[0].content.contains("What's the core idea?"))
        // ...but not repeated later.
        assertEquals(ChatRole.ASSISTANT, wire[1].role)
        assertEquals("Small habits compound.", wire[1].content)
        assertEquals(ChatRole.USER, wire[2].role)
        assertEquals("Tell me more about chapter 2.", wire[2].content)
        assertTrue(!wire[2].content.contains("Title:"))
    }

    @Test
    fun `ask delegates the built wire messages to the provider`() = runBlocking {
        val result = repo.ask(book(), history = emptyList(), question = "Hello?")

        assertEquals("canned reply", result.getOrNull())
        val sent = provider.lastMessages!!
        assertEquals(1, sent.size)
        assertTrue(sent[0].content.contains("Hello?"))
        assertTrue(sent[0].content.contains("Title: Atomic Habits"))
    }

    @Test
    fun `suggested questions reference the author and current chapter`() {
        val suggestions = repo.suggestedQuestions(book())
        assertTrue(suggestions.any { it.contains("James Clear") })
        assertTrue(suggestions.any { it.contains("Make It Obvious") }) // current chapter (2)
    }
}
