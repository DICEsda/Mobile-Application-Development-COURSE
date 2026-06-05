package com.audiobook.app.data.remote.llm

/**
 * A single message in an LLM conversation, in the app's own domain terms.
 *
 * This is intentionally decoupled from any provider's wire format (e.g. the
 * OpenAI `{role, content}` JSON used by LM Studio). Providers translate to/from
 * their own DTOs, so the rest of the app never depends on a vendor schema.
 */
data class ChatMessage(
    val role: ChatRole,
    val content: String
) {
    companion object {
        fun system(content: String) = ChatMessage(ChatRole.SYSTEM, content)
        fun user(content: String) = ChatMessage(ChatRole.USER, content)
        fun assistant(content: String) = ChatMessage(ChatRole.ASSISTANT, content)
    }
}

/**
 * The author of a [ChatMessage]. [wireName] is the lowercase string most
 * OpenAI-compatible servers expect on the wire.
 */
enum class ChatRole(val wireName: String) {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant")
}
