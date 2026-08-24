package com.example.lmstudioclient.ui.chat

import com.example.lmstudioclient.data.local.ChatMessageEntity
import com.example.lmstudioclient.data.local.ChatSessionEntity

sealed interface ChatUiState {
    object Idle : ChatUiState
    object Loading : ChatUiState
    data class Success(val lastMessage: ChatMessageEntity) : ChatUiState
    data class Error(val message: String) : ChatUiState
}

data class ChatScreenState(
    val activeSession: ChatSessionEntity? = null,
    val sessions: List<ChatSessionEntity> = emptyList(),
    val messages: List<ChatMessageEntity> = emptyList(),
    val isGenerating: Boolean = false,
    val errorMessage: String? = null,
    val pendingAttachments: List<String> = emptyList(),
    val viewingAttachment: String? = null
)
