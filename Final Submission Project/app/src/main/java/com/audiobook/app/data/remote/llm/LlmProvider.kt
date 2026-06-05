package com.audiobook.app.data.remote.llm

/**
 * The seam between the app and whatever Large Language Model backs the
 * "Book Companion" feature.
 *
 * The UI and the companion repository depend only on this interface — never on
 * LM Studio, OpenAI, Gemini, or an on-device model. Swapping the backend is
 * adding one implementation; no caller changes. (See DECISIONS.md — dependency
 * inversion for the LLM integration.)
 *
 * All calls return [Result] so callers degrade gracefully when the model server
 * is unreachable (the common case for a local LLM that isn't running).
 */
interface LlmProvider {

    /** Human-readable name for settings/diagnostics, e.g. "LM Studio (local)". */
    val displayName: String

    /**
     * Send a conversation and get the assistant's reply.
     *
     * @param messages ordered history; typically a [ChatRole.SYSTEM] grounding
     *                 prompt followed by alternating user/assistant turns.
     * @param temperature sampling temperature (0.0 = deterministic).
     */
    suspend fun chat(messages: List<ChatMessage>, temperature: Float = 0.7f): Result<String>

    /**
     * List the model ids the server currently has available. Used by Settings
     * to verify connectivity and let the user pick a loaded model.
     */
    suspend fun listModels(): Result<List<String>>
}

/**
 * Runtime configuration for an [LlmProvider]. Sourced from user preferences so
 * the LM Studio host/model can be changed without rebuilding the app.
 *
 * The default base URL targets the Android emulator's host loopback
 * (10.0.2.2 -> the dev machine's localhost). On a physical device, use the
 * machine's LAN IP, or `adb reverse tcp:1234 tcp:1234` and point at
 * `http://localhost:1234`.
 */
data class LlmConfig(
    val baseUrl: String = DEFAULT_BASE_URL,
    val model: String = DEFAULT_MODEL,
    val enabled: Boolean = false
) {
    companion object {
        const val DEFAULT_BASE_URL = "http://10.0.2.2:1234/"
        // Matches the model loaded in LM Studio during setup. Any loaded model
        // works — the user can change this in Settings (verified via listModels).
        const val DEFAULT_MODEL = "mistralai/mistral-7b-instruct-v0.3"
    }
}

/** Thrown for LLM-specific failures that aren't transport exceptions. */
class LlmException(message: String) : Exception(message)
