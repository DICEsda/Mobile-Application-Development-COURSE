package com.audiobook.app.data.remote.llm

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * [LlmProvider] backed by a local LM Studio server (OpenAI-compatible API).
 *
 * The base URL and model are read fresh from [configProvider] on every call, so
 * changing them in Settings takes effect immediately. The underlying Retrofit
 * client is cached and only rebuilt when the base URL actually changes.
 */
class LmStudioProvider(
    private val configProvider: suspend () -> LlmConfig
) : LlmProvider {

    override val displayName: String = "LM Studio (local)"

    // Local models can take a while to generate; keep read timeout generous.
    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private var cachedBaseUrl: String? = null
    private var cachedApi: LmStudioApi? = null

    private fun api(baseUrl: String): LmStudioApi {
        val normalized = normalizeBaseUrl(baseUrl)
        val current = cachedApi
        if (current != null && cachedBaseUrl == normalized) return current

        val api = Retrofit.Builder()
            .baseUrl(normalized)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LmStudioApi::class.java)
        cachedApi = api
        cachedBaseUrl = normalized
        return api
    }

    override suspend fun chat(messages: List<ChatMessage>, temperature: Float): Result<String> {
        val config = configProvider()
        return try {
            val request = ChatCompletionRequest(
                model = config.model,
                messages = messages.map { WireMessage(it.role.wireName, it.content) },
                temperature = temperature,
                stream = false
            )
            val response = api(config.baseUrl).chatCompletions(request)
            val content = response.choices.firstOrNull()?.message?.content
                ?: return Result.failure(LlmException("LM Studio returned no message content"))
            Result.success(content.trim())
        } catch (e: Exception) {
            Log.e(TAG, "chat() failed against ${config.baseUrl}", e)
            Result.failure(e)
        }
    }

    override suspend fun listModels(): Result<List<String>> {
        val config = configProvider()
        return try {
            val response = api(config.baseUrl).listModels()
            Result.success(response.data.map { it.id })
        } catch (e: Exception) {
            Log.e(TAG, "listModels() failed against ${config.baseUrl}", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "LmStudioProvider"

        /**
         * Retrofit requires an absolute base URL ending in '/'. Users may type
         * "192.168.1.50:1234", "http://host:1234", or with a trailing slash —
         * normalize all of them. Pure function, unit-tested.
         */
        fun normalizeBaseUrl(raw: String): String {
            var url = raw.trim()
            if (url.isEmpty()) return url
            if (!url.startsWith("http://", ignoreCase = true) &&
                !url.startsWith("https://", ignoreCase = true)
            ) {
                url = "http://$url"
            }
            if (!url.endsWith("/")) url = "$url/"
            return url
        }
    }
}
