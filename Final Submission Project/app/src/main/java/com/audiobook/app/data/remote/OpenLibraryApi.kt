package com.audiobook.app.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * OpenLibrary API Service
 * 
 * OpenLibrary is a free, open-source library catalog with millions of books.
 * No API key required - perfect for academic projects.
 * 
 * Documentation: https://openlibrary.org/developers/api
 */
interface OpenLibraryApi {
    
    /**
     * Search for books by query.
     * 
     * @param query Search query (title, author, ISBN, etc.)
     * @param limit Maximum number of results
     * @return SearchResponse with matching books
     */
    @GET("search.json")
    suspend fun searchBooks(
        @Query("q") query: String,
        @Query("limit") limit: Int = 10,
        @Query("fields") fields: String = "key,title,author_name,first_publish_year,cover_i,isbn,number_of_pages_median"
    ): SearchResponse
    
    /**
     * Search by title.
     */
    @GET("search.json")
    suspend fun searchByTitle(
        @Query("title") title: String,
        @Query("limit") limit: Int = 10
    ): SearchResponse
    
    /**
     * Search by author.
     */
    @GET("search.json")
    suspend fun searchByAuthor(
        @Query("author") author: String,
        @Query("limit") limit: Int = 10
    ): SearchResponse
    
    /**
     * Search by ISBN.
     */
    @GET("search.json")
    suspend fun searchByIsbn(
        @Query("isbn") isbn: String
    ): SearchResponse
    
    /**
     * Get book details by work key.
     * 
     * @param workKey The work key (e.g., "OL45883W")
     * @return WorkDetails with full book information
     */
    @GET("works/{workKey}.json")
    suspend fun getWorkDetails(
        @Path("workKey") workKey: String
    ): WorkDetails
    
    /**
     * Get author details.
     * 
     * @param authorKey The author key (e.g., "OL23919A")
     * @return AuthorDetails with author information
     */
    @GET("authors/{authorKey}.json")
    suspend fun getAuthorDetails(
        @Path("authorKey") authorKey: String
    ): AuthorDetails
}

// ============================================================
// Response Models
// ============================================================

data class SearchResponse(
    val numFound: Int = 0,
    val start: Int = 0,
    val docs: List<BookDoc> = emptyList()
)

data class BookDoc(
    val key: String? = null, // e.g., "/works/OL45883W"
    val title: String? = null,
    val author_name: List<String>? = null,
    val first_publish_year: Int? = null,
    val cover_i: Int? = null, // Cover ID for building cover URL
    val isbn: List<String>? = null,
    val number_of_pages_median: Int? = null,
    val subject: List<String>? = null,
    val language: List<String>? = null
) {
    /**
     * Get the primary author name.
     */
    val primaryAuthor: String
        get() = author_name?.firstOrNull() ?: "Unknown Author"
    
    /**
     * Build cover image URL from cover ID.
     * 
     * @param size Cover size: S (small), M (medium), L (large)
     * @return URL to cover image, or null if no cover
     */
    fun getCoverUrl(size: String = "L"): String? {
        return cover_i?.let { coverId ->
            "https://covers.openlibrary.org/b/id/$coverId-$size.jpg"
        }
    }
    
    /**
     * Get the work key without the "/works/" prefix.
     */
    val workKey: String?
        get() = key?.removePrefix("/works/")
}

data class WorkDetails(
    val title: String? = null,
    val description: Any? = null, // Can be String or { value: String }
    val subjects: List<String>? = null,
    val covers: List<Int>? = null,
    val authors: List<AuthorRef>? = null,
    val first_publish_date: String? = null
) {
    /**
     * Get description as a string (handles both formats).
     */
    val descriptionText: String?
        get() = when (description) {
            is String -> description
            is Map<*, *> -> (description as Map<*, *>)["value"] as? String
            else -> null
        }
    
    /**
     * Get the primary cover URL.
     */
    fun getCoverUrl(size: String = "L"): String? {
        return covers?.firstOrNull()?.let { coverId ->
            "https://covers.openlibrary.org/b/id/$coverId-$size.jpg"
        }
    }
}

data class AuthorRef(
    val author: AuthorKey? = null,
    val type: TypeRef? = null
)

data class AuthorKey(
    val key: String? = null // e.g., "/authors/OL23919A"
)

data class TypeRef(
    val key: String? = null
)

data class AuthorDetails(
    val name: String? = null,
    val bio: Any? = null, // Can be String or { value: String }
    val birth_date: String? = null,
    val death_date: String? = null,
    val photos: List<Int>? = null
) {
    /**
     * Get bio as a string.
     */
    val bioText: String?
        get() = when (bio) {
            is String -> bio
            is Map<*, *> -> (bio as Map<*, *>)["value"] as? String
            else -> null
        }
    
    /**
     * Get author photo URL.
     */
    fun getPhotoUrl(size: String = "M"): String? {
        return photos?.firstOrNull()?.let { photoId ->
            "https://covers.openlibrary.org/a/id/$photoId-$size.jpg"
        }
    }
}
