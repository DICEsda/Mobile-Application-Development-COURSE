package com.audiobook.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.audiobook.app.data.repository.AuthRepository
import kotlinx.coroutines.launch

@Composable
fun SignUpScreen(
    authRepository: AuthRepository,
    onSignUpSuccess: () -> Unit,
    onNavigateToSignIn: () -> Unit
) {
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val scope = rememberCoroutineScope()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Create Account",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )
        
        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("Confirm Password") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        errorMessage?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = {
                if (email.isBlank() || password.isBlank() || confirmPassword.isBlank()) {
                    errorMessage = "Please fill all fields"
                    return@Button
                }
                
                if (password != confirmPassword) {
                    errorMessage = "Passwords do not match"
                    return@Button
                }
                
                if (password.length < 6) {
                    errorMessage = "Password must be at least 6 characters"
                    return@Button
                }
                
                isLoading = true
                errorMessage = null
                scope.launch {
                    val result = authRepository.signUp(email, password)
                    result.fold(
                        onSuccess = {
                            if (displayName.isNotBlank()) {
                                authRepository.updateDisplayName(displayName.trim())
                            }
                            isLoading = false
                            onSignUpSuccess()
                        },
                        onFailure = { e ->
                            isLoading = false
                            errorMessage = e.message ?: "Sign up failed"
                        }
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Sign Up")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        TextButton(
            onClick = onNavigateToSignIn,
            enabled = !isLoading
        ) {
            Text("Already have an account? Sign In")
        }
    }
}
