package com.audiobook.app.data.repository

import com.audiobook.app.data.model.Audiobook
import com.audiobook.app.data.remote.llm.ChatMessage
import com.audiobook.app.data.remote.llm.LlmProvider

/**
 * Drives the "Book Companion" chat: turns an [Audiobook] plus a conversation
 * history into a grounded request for an [LlmProvider], and returns the reply.
 *
 * Design notes:
 * - The repository depends on the [LlmProvider] interface, never on LM Studio.
 * - Grounding (book context + instructions) is composed into the FIRST user
 *   message rather than a system message. Local models vary in system-role
 *   support — Mistral's template rejects it outright — and strict user/assistant
 *   alternation is the safest common denominator.
 * - [buildGroundingPreamble] and [buildWireMessages] are pure functions so the
 *   prompt construction can be unit-tested without a network/model.
 */
class BookCompanionRepository(
    private val provider: LlmProvider
) {

    /**
     * Ask a question about [book], given the prior display [history]
     * (alternating user/assistant turns, WITHOUT any grounding text).
     */
    suspend fun ask(
        book: Audiobook,
        history: List<ChatMessage>,
        question: String
    ): Result<String> {
        return provider.chat(buildWireMessages(book, history, question))
    }

    /** Verify the configured model server is reachable (for Settings). */
    suspend fun listModels(): Result<List<String>> = provider.listModels()

    /**
     * Build the messages actually sent to the model: prior turns plus the new
     * question, with the grounding preamble merged into the first user message
     * so it stays in context for the whole conversation while preserving strict
     * user/assistant alternation.
     */
    fun buildWireMessages(
        book: Audiobook,
        history: List<ChatMessage>,
        question: String
    ): List<ChatMessage> {
        val turns = history + ChatMessage.user(question)
        val first = turns.first()
        val groundedFirst = ChatMessage.user(
            buildGroundingPreamble(book) + "\n\nQUESTION:\n" + first.content
        )
        return listOf(groundedFirst) + turns.drop(1)
    }

    /** The instruction + book-context block prepended to the first question. */
    fun buildGroundingPreamble(book: Audiobook): String {
        val sb = StringBuilder()
        sb.appendLine(
            "You are a knowledgeable, concise companion inside an audiobook app. " +
                "Help the listener understand and reflect on the book described below. " +
                "Use only the provided context; if something isn't covered, say so briefly " +
                "instead of inventing details. Prefer not to spoil content beyond the " +
                "listener's current position. Keep answers focused — a few sentences unless " +
                "asked for more."
        )
        sb.appendLine()
        sb.appendLine("BOOK CONTEXT")
        sb.appendLine("Title: ${book.title}")
        sb.appendLine("Author: ${book.author}")
        if (!book.narrator.isNullOrBlank()) sb.appendLine("Narrator: ${book.narrator}")
        val description = book.description?.takeIf { it.isNotBlank() } ?: "(no description available)"
        sb.appendLine("Description: $description")

        if (book.chapters.isNotEmpty()) {
            sb.appendLine("Chapters (${book.chapters.size}):")
            sb.appendLine(formatChapterList(book))
        }

        val total = book.chapters.size
        val position = if (total > 0) {
            "Chapter ${book.currentChapter} of $total"
        } else {
            "Chapter ${book.currentChapter}"
        }
        sb.append("Listener's current position: $position")
        return sb.toString()
    }

    /** Suggested opening questions for the chat's empty state. */
    fun suggestedQuestions(book: Audiobook): List<String> {
        val questions = mutableListOf(
            "What's the core idea of this book?",
            "Who is ${book.author} and what are they known for?"
        )
        book.chapters.getOrNull(book.currentChapter - 1)?.let { current ->
            questions.add("What is the chapter \"${current.title}\" about?")
        }
        questions.add("What are the key takeaways so far?")
        return questions
    }

    private fun formatChapterList(book: Audiobook): String {
        val chapters = book.chapters
        val shown = chapters.take(MAX_CHAPTERS_IN_PROMPT)
        val lines = shown.joinToString("; ") { "${it.number}. ${it.title}" }
        return if (chapters.size > MAX_CHAPTERS_IN_PROMPT) {
            "$lines; … (${chapters.size - MAX_CHAPTERS_IN_PROMPT} more)"
        } else {
            lines
        }
    }

    companion object {
        private const val MAX_CHAPTERS_IN_PROMPT = 50
    }
}
