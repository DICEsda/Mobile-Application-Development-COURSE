package com.audiobook.app.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.audiobook.app.R
import com.audiobook.app.appContainer
import com.audiobook.app.data.model.Audiobook
import com.audiobook.app.data.remote.llm.ChatMessage
import com.audiobook.app.data.remote.llm.ChatRole
import com.audiobook.app.ui.theme.*
import com.audiobook.app.ui.viewmodel.BookCompanionViewModel

/**
 * The "Book Companion" chat: a conversation grounded in the given [book]'s
 * metadata, answered by the configured local LLM.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookCompanionScreen(
    book: Audiobook,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: BookCompanionViewModel = viewModel(
        factory = BookCompanionViewModel.Factory(context.appContainer.bookCompanionRepository)
    )

    LaunchedEffect(book.id) { viewModel.start(book) }

    val state by viewModel.uiState.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Keep the newest message in view as the conversation grows.
    LaunchedEffect(state.messages.size, state.isLoading) {
        val count = state.messages.size + if (state.isLoading) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    fun submit() {
        val text = input.trim()
        if (text.isNotEmpty()) {
            viewModel.send(text)
            input = ""
        }
    }

    Scaffold(
        containerColor = Surface1,
        topBar = {
            Surface(color = Surface1, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = ImageVector.vectorResource(R.drawable.ic_book_sparkle),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Book Companion", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        }
                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1
                        )
                    }
                }
            }
        },
        bottomBar = {
            ChatInputBar(
                value = input,
                onValueChange = { input = it },
                onSend = ::submit,
                enabled = !state.isLoading
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.messages.isEmpty() && !state.isLoading) {
                item {
                    EmptyState(
                        suggestions = state.suggestions,
                        onSuggestionClick = { viewModel.send(it) }
                    )
                }
            }

            itemsIndexed(state.messages) { index, message ->
                val isLast = index == state.messages.lastIndex
                val isStreamingThis = isLast && state.isLoading && message.role == ChatRole.ASSISTANT
                if (message.role == ChatRole.ASSISTANT && message.content.isEmpty() && isStreamingThis) {
                    // Waiting on the first token.
                    TypingIndicator()
                } else {
                    MessageBubble(message = message, isStreaming = isStreamingThis)
                }
            }

            state.error?.let { error ->
                item {
                    Surface(
                        color = ErrorRed.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = ErrorRed,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    suggestions: List<String>,
    onSuggestionClick: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Ask me about this book",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Answers are based on the book's title, author, description and chapters.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        Spacer(Modifier.height(16.dp))
        suggestions.forEach { suggestion ->
            Surface(
                color = Surface2,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clickable { onSuggestionClick(suggestion) }
            ) {
                Text(
                    text = suggestion,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, isStreaming: Boolean) {
    if (message.role == ChatRole.USER) {
        // User turns stay in a compact teal bubble, right-aligned.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .wrapContentWidth(Alignment.End)
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }
    } else {
        // Assistant turns are free in the shell — no card — and fade in.
        AssistantMessage(text = message.content, isStreaming = isStreaming)
    }
}

/**
 * Assistant reply rendered directly on the background (no bubble). Fades in
 * when it first appears and shows a blinking caret while still streaming.
 */
@Composable
private fun AssistantMessage(text: String, isStreaming: Boolean) {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val fade by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 450),
        label = "assistantFade"
    )

    val caret by rememberInfiniteTransition(label = "caret").animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(650, easing = LinearEasing), RepeatMode.Reverse),
        label = "caretAlpha"
    )

    Row(modifier = Modifier.fillMaxWidth().alpha(fade)) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary
        )
        if (isStreaming) {
            Text(
                text = "▍",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.alpha(caret)
            )
        }
    }
}

@Composable
private fun TypingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(10.dp))
        Text("Thinking…", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean
) {
    Surface(color = Surface1, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask about this book…", color = TextTertiary) },
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (enabled) onSend() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Surface2,
                    unfocusedContainerColor = Surface2,
                    disabledContainerColor = Surface2,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )
            Spacer(Modifier.width(8.dp))
            val canSend = enabled && value.isNotBlank()
            IconButton(
                onClick = onSend,
                enabled = canSend,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        if (canSend) MaterialTheme.colorScheme.primary else Surface3
                    )
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (canSend) TextPrimary else TextTertiary
                )
            }
        }
    }
}
