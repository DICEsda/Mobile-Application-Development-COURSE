package com.audiobook.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Repository for Firebase Authentication.
 * 
 * Handles user sign-in, sign-up, and authentication state management.
 * Uses Kotlin coroutines and Flow for reactive state updates.
 */
class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    
    /**
     * Current authenticated user (null if not signed in).
     */
    val currentUser: FirebaseUser?
        get() = auth.currentUser
    
    /**
     * Flow that emits the current user state.
     * Emits null when signed out, FirebaseUser when signed in.
     */
    val authState: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }
    
    /**
     * Check if user is currently signed in.
     */
    val isSignedIn: Boolean
        get() = currentUser != null
    
    /**
     * Sign up with email and password.
     * 
     * @param email User's email address
     * @param password User's password (min 6 characters)
     * @return Result with FirebaseUser on success, or exception on failure
     */
    suspend fun signUp(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            result.user?.let { 
                Result.success(it) 
            } ?: Result.failure(Exception("User creation failed"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Sign in with email and password.
     * 
     * @param email User's email address
     * @param password User's password
     * @return Result with FirebaseUser on success, or exception on failure
     */
    suspend fun signIn(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            result.user?.let { 
                Result.success(it) 
            } ?: Result.failure(Exception("Sign in failed"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Sign out the current user.
     */
    fun signOut() {
        auth.signOut()
    }
    
    /**
     * Send password reset email.
     * 
     * @param email Email address to send reset link
     * @return Result indicating success or failure
     */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Delete the current user's account.
     * 
     * @return Result indicating success or failure
     */
    suspend fun deleteAccount(): Result<Unit> {
        return try {
            currentUser?.delete()?.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Update the user's display name.
     */
    suspend fun updateDisplayName(displayName: String): Result<Unit> {
        return try {
            val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                .setDisplayName(displayName)
                .build()
            currentUser?.updateProfile(profileUpdates)?.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Data class representing authentication state for UI.
 */
data class AuthState(
    val isLoading: Boolean = false,
    val user: FirebaseUser? = null,
    val error: String? = null
) {
    val isSignedIn: Boolean get() = user != null
    val displayName: String get() = user?.displayName ?: user?.email?.substringBefore("@") ?: "User"
    val email: String get() = user?.email ?: ""
}
