package es.zombielawng.nom.homeandi.ui.chat

import es.zombielawng.nom.homeandi.data.local.ChatMessageEntity
import es.zombielawng.nom.homeandi.data.local.ChatSessionEntity
import es.zombielawng.nom.homeandi.data.remote.ModelData

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
    val viewingAttachment: String? = null,
    val loadedModels: List<ModelData> = emptyList()
)
