package es.zombielawng.nom.homeandi.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import es.zombielawng.nom.homeandi.data.local.ChatSessionEntity
import es.zombielawng.nom.homeandi.data.preferences.ServerPreferencesManager
import es.zombielawng.nom.homeandi.data.remote.ModelData
import es.zombielawng.nom.homeandi.data.repository.ChatRepository
import es.zombielawng.nom.homeandi.data.worker.ChatWorker
import es.zombielawng.nom.homeandi.util.NetworkResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ChatViewModel(
    private val application: Application,
    private val repository: ChatRepository,
    private val preferencesManager: ServerPreferencesManager,
    private val workManager: WorkManager = WorkManager.getInstance(application)
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ChatScreenState())
    val uiState: StateFlow<ChatScreenState> = _uiState.asStateFlow()

    private var messageSubscriptionJob: Job? = null
    private var workStatusJob: Job? = null
    private var globalWorkStatusJob: Job? = null
    private var pendingActiveSessionId: String? = null
    private var currentWorkRequestId: java.util.UUID? = null

    init {
        observeSessions()
        fetchLoadedModels()
        observeGlobalWorkStatus()
        observeScreenshotsEnabledSetting()
    }

    private fun observeScreenshotsEnabledSetting() {
        viewModelScope.launch {
            preferencesManager.screenshotsEnabledFlow.collect { enabled ->
                _uiState.update { it.copy(isScreenshotsEnabled = enabled) }
            }
        }
    }

    private fun observeSessions() {
        viewModelScope.launch {
            repository.getAllSessions().collect { sessionList ->
                _uiState.update { currentState ->
                    val lastSessionId = preferencesManager.getLastSessionIdSync()
                    val targetId = pendingActiveSessionId ?: currentState.activeSession?.id ?: lastSessionId
                    
                    val updatedActiveSession = if (targetId != null) {
                        sessionList.find { it.id == targetId } ?: sessionList.firstOrNull()
                    } else {
                        sessionList.firstOrNull()
                    }
                    
                    // If we found our pending session, clear the flag
                    if (updatedActiveSession?.id == pendingActiveSessionId) {
                        pendingActiveSessionId = null
                    }
                    
                    currentState.copy(
                        sessions = sessionList,
                        activeSession = updatedActiveSession
                    )
                }

                val currentActive = _uiState.value.activeSession
                if (currentActive != null) {
                    observeMessagesForSession(currentActive.id)
                } else if (sessionList.isEmpty()) {
                    createNewSession()
                }
            }
        }
    }

    fun selectSession(sessionId: String) {
        pendingActiveSessionId = sessionId
        preferencesManager.updateLastSessionId(sessionId)
        val targetSession = _uiState.value.sessions.find { it.id == sessionId }
        if (targetSession != null) {
            _uiState.update { it.copy(activeSession = targetSession, errorMessage = null) }
            observeMessagesForSession(sessionId)
        }
    }

    private fun observeMessagesForSession(sessionId: String) {
        messageSubscriptionJob?.cancel()
        messageSubscriptionJob = viewModelScope.launch {
            repository.getMessagesForSession(sessionId).collect { messageList ->
                _uiState.update { it.copy(messages = messageList) }
            }
        }
        observeWorkStatus(sessionId)
    }

    private fun observeWorkStatus(sessionId: String) {
        workStatusJob?.cancel()
        workStatusJob = viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow("send_message_$sessionId")
                .collect { workInfos ->
                    val lastWork = workInfos.lastOrNull()
                    if (lastWork != null) {
                        _uiState.update { state ->
                            val isWorking = (lastWork.state == WorkInfo.State.RUNNING || 
                                          lastWork.state == WorkInfo.State.ENQUEUED)
                            
                            // If we have a specific request we're waiting for, only stop generating
                            // if THAT request is finished.
                            val shouldUpdateGenerating = if (currentWorkRequestId != null) {
                                lastWork.id == currentWorkRequestId || !isWorking
                            } else {
                                true
                            }

                            state.copy(
                                isGenerating = if (shouldUpdateGenerating) isWorking else state.isGenerating,
                                errorMessage = if (lastWork.state == WorkInfo.State.FAILED) {
                                    lastWork.outputData.getString(ChatWorker.KEY_ERROR_MESSAGE) ?: "Failed to send message"
                                } else {
                                    state.errorMessage
                                }
                            )
                        }
                    } else {
                        // If no work found for this session, it's not generating
                        _uiState.update { it.copy(isGenerating = false) }
                    }
                }
        }
    }

    private fun observeGlobalWorkStatus() {
        globalWorkStatusJob?.cancel()
        globalWorkStatusJob = viewModelScope.launch {
            workManager.getWorkInfosByTagFlow(TAG_CHAT_WORK)
                .collect { workInfos ->
                    val activeWork = workInfos.find { 
                        it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED 
                    }
                    
                    val generatingId = activeWork?.tags?.find { it.startsWith(TAG_PREFIX_SESSION) }
                        ?.removePrefix(TAG_PREFIX_SESSION)

                    _uiState.update { state ->
                        state.copy(
                            isAnySessionGenerating = activeWork != null,
                            generatingSessionId = generatingId
                        )
                    }
                }
        }
    }

    fun fetchLoadedModels() {
        viewModelScope.launch {
            val result = repository.testConnection()
            if (result is NetworkResult.Success) {
                val loaded = result.data.filter { m -> m.isLoaded }
                _uiState.update { it.copy(loadedModels = loaded) }

                // If the current saved model is the default "local-model" placeholder,
                // automatically update it to the first loaded model to prevent 404 errors.
                val currentSavedModel = preferencesManager.getModelSync()
                if (currentSavedModel == ServerPreferencesManager.DEFAULT_MODEL && loaded.isNotEmpty()) {
                    val firstLoadedId = loaded.first().id
                    preferencesManager.updateServerConfig(
                        host = preferencesManager.getHostSync(),
                        port = preferencesManager.getPortSync(),
                        model = firstLoadedId,
                        systemPrompt = preferencesManager.getSystemPromptSync(firstLoadedId),
                        apiKey = preferencesManager.getApiKeySync()
                    )

                    // Also, if the active session is currently using "local-model", update it too.
                    val activeSession = _uiState.value.activeSession
                    if (activeSession != null && activeSession.modelName == ServerPreferencesManager.DEFAULT_MODEL) {
                        updateSessionModel(activeSession.id, firstLoadedId)
                    }
                }
            }
        }
    }

    fun createNewSession(title: String = "New Chat", modelId: String? = null) {
        viewModelScope.launch {
            val defaultModel = preferencesManager.getModelSync()
            val model = modelId 
                ?: _uiState.value.loadedModels.firstOrNull { it.id == defaultModel }?.id
                ?: _uiState.value.loadedModels.firstOrNull()?.id
                ?: defaultModel
            val newSession = repository.createNewSession(title = title, modelName = model)
            pendingActiveSessionId = newSession.id
            _uiState.update { it.copy(activeSession = newSession, errorMessage = null) }
            observeMessagesForSession(newSession.id)
        }
    }

    fun updateSessionModel(sessionId: String, modelId: String) {
        viewModelScope.launch {
            val session = _uiState.value.sessions.find { it.id == sessionId } ?: return@launch
            val updatedSession = session.copy(modelName = modelId)
            repository.updateSession(updatedSession)
            
            _uiState.update { state ->
                val updatedList = state.sessions.map { if (it.id == sessionId) updatedSession else it }
                state.copy(
                    sessions = updatedList,
                    activeSession = if (state.activeSession?.id == sessionId) updatedSession else state.activeSession
                )
            }
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
        }
    }

    fun updateSessionScreenshot(sessionId: String, path: String?) {
        viewModelScope.launch {
            val session = _uiState.value.sessions.find { it.id == sessionId } ?: return@launch
            val updated = session.copy(screenshotPath = path)
            repository.updateSession(updated)
        }
    }

    fun renameSession(sessionId: String, newTitle: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch {
            val session = _uiState.value.sessions.find { it.id == sessionId } ?: return@launch
            val updated = session.copy(title = newTitle)
            repository.updateSession(updated)
        }
    }

    fun stopGeneration(sessionId: String) {
        workManager.cancelUniqueWork("send_message_$sessionId")
        _uiState.update { it.copy(isGenerating = false, isAnySessionGenerating = false, generatingSessionId = null) }
    }

    fun addAttachment(path: String) {
        if (path.isBlank()) return
        _uiState.update { state ->
            val updated = state.pendingAttachments.toMutableList().apply { add(path) }
            state.copy(pendingAttachments = updated)
        }
    }

    fun removeAttachment(path: String) {
        _uiState.update { state ->
            val updated = state.pendingAttachments.toMutableList().apply { remove(path) }
            state.copy(pendingAttachments = updated)
        }
    }

    fun openAttachment(path: String) {
        _uiState.update { it.copy(viewingAttachment = path) }
    }

    fun closeAttachment() {
        _uiState.update { it.copy(viewingAttachment = null) }
    }

    fun sendMessage(userText: String) {
        val currentSession = _uiState.value.activeSession ?: return
        if (userText.isBlank() && _uiState.value.pendingAttachments.isEmpty()) return
        if (_uiState.value.isAnySessionGenerating) return

        val attachments = _uiState.value.pendingAttachments.toList()
        _uiState.update { it.copy(isGenerating = true, errorMessage = null, pendingAttachments = emptyList()) }

        val data = androidx.work.workDataOf(
            ChatWorker.KEY_SESSION_ID to currentSession.id,
            ChatWorker.KEY_USER_PROMPT to userText,
            ChatWorker.KEY_ATTACHMENTS to attachments.toTypedArray()
        )

        val request = androidx.work.OneTimeWorkRequestBuilder<ChatWorker>()
            .setInputData(data)
            .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(TAG_CHAT_WORK)
            .addTag("$TAG_PREFIX_SESSION${currentSession.id}")
            .build()

        currentWorkRequestId = request.id

        workManager.enqueueUniqueWork(
            "send_message_${currentSession.id}",
            androidx.work.ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    companion object {
        const val TAG_CHAT_WORK = "chat_work"
        const val TAG_PREFIX_SESSION = "session_id_"
    }

    class Factory(
        private val application: Application,
        private val repository: ChatRepository,
        private val preferencesManager: ServerPreferencesManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(application, repository, preferencesManager) as T
        }
    }
}
