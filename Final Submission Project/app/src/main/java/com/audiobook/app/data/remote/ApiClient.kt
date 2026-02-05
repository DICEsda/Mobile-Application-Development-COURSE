package com.audiobook.app.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Factory for creating Retrofit API clients.
 * 
 * Uses OkHttp with logging interceptor for network debugging.
 * Singleton pattern ensures only one instance of each client exists.
 */
object ApiClient {
    
    private const val OPEN_LIBRARY_BASE_URL = "https://openlibrary.org/"
    
    // Connection timeouts (in seconds)
    private const val CONNECT_TIMEOUT = 30L
    private const val READ_TIMEOUT = 30L
    private const val WRITE_TIMEOUT = 30L
    
    /**
     * OkHttp client with logging interceptor.
     * Logs full request/response bodies in debug builds.
     */
    private val okHttpClient: OkHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            // Use BODY for full logging in debug, NONE in release
            level = HttpLoggingInterceptor.Level.BASIC
        }
        
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                // Add User-Agent header (OpenLibrary recommends this)
                val request = chain.request().newBuilder()
                    .header("User-Agent", "AudiobookPlayer/1.0 (Android; Academic Project)")
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
    
    /**
     * Retrofit instance for OpenLibrary API.
     */
    private val openLibraryRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(OPEN_LIBRARY_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    /**
     * Get the OpenLibrary API service instance.
     */
    val openLibraryApi: OpenLibraryApi by lazy {
        openLibraryRetrofit.create(OpenLibraryApi::class.java)
    }
}

/**
 * Repository wrapper for OpenLibrary API operations.
 * Provides a clean interface for fetching book metadata.
 */
class BookMetadataRepository(
    private val api: OpenLibraryApi = ApiClient.openLibraryApi
) {
    
    /**
     * Search for book metadata by title.
     * Useful for enriching audiobook data with cover images and descriptions.
     * 
     * @param title The book title to search for
     * @return BookDoc with metadata, or null if not found
     */
    suspend fun searchByTitle(title: String): BookDoc? {
        return try {
            val response = api.searchByTitle(title, limit = 1)
            response.docs.firstOrNull()
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Search for book metadata by title and author for more accurate results.
     * 
     * @param title The book title
     * @param author The author name
     * @return BookDoc with metadata, or null if not found
     */
    suspend fun searchByTitleAndAuthor(title: String, author: String): BookDoc? {
        return try {
            val query = "$title $author"
            val response = api.searchBooks(query, limit = 1)
            response.docs.firstOrNull()
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get a cover URL for a book.
     * Tries to find the best available cover image.
     * 
     * @param title The book title
     * @param author Optional author name for better matching
     * @param size Cover size: S (small), M (medium), L (large)
     * @return URL to cover image, or null if not found
     */
    suspend fun getCoverUrl(title: String, author: String? = null, size: String = "L"): String? {
        val book = if (author != null) {
            searchByTitleAndAuthor(title, author)
        } else {
            searchByTitle(title)
        }
        return book?.getCoverUrl(size)
    }
    
    /**
     * Get detailed information about a work.
     * 
     * @param workKey The OpenLibrary work key (e.g., "OL45883W")
     * @return WorkDetails with full book information
     */
    suspend fun getWorkDetails(workKey: String): WorkDetails? {
        return try {
            api.getWorkDetails(workKey)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Search books with general query.
     * 
     * @param query Search query
     * @param limit Maximum results
     * @return List of matching books
     */
    suspend fun searchBooks(query: String, limit: Int = 10): List<BookDoc> {
        return try {
            api.searchBooks(query, limit).docs
        } catch (e: Exception) {
            emptyList()
        }
    }
}
