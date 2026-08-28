package es.zombielawng.nom.homeandi.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import es.zombielawng.nom.homeandi.data.preferences.ServerPreferencesManager
import es.zombielawng.nom.homeandi.data.remote.ModelData
import es.zombielawng.nom.homeandi.data.repository.ChatRepository
import es.zombielawng.nom.homeandi.ui.components.ConnectionState
import es.zombielawng.nom.homeandi.util.NetworkResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val host: String = ServerPreferencesManager.DEFAULT_HOST,
    val port: String = ServerPreferencesManager.DEFAULT_PORT.toString(),
    val apiKey: String = ServerPreferencesManager.DEFAULT_API_KEY,
    val modelName: String = ServerPreferencesManager.DEFAULT_MODEL,
    val systemPrompt: String = ServerPreferencesManager.DEFAULT_SYSTEM_PROMPT,
    val isDarkMode: Boolean? = null, // null means follow system
    val connectionState: ConnectionState = ConnectionState.Idle,
    val availableModels: List<ModelData> = emptyList(),
    val saveMessage: String? = null,
    val isModelActionLoading: Boolean = false,
    val modelActionMessage: String? = null,
    val isScreenshotsEnabled: Boolean = true,
    val themeMode: String = "system",
    val isWebSearchEnabled: Boolean = false
)

class SettingsViewModel(
    private val preferencesManager: ServerPreferencesManager,
    private val repository: ChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadPreferences()
        fetchModels()
    }

    private fun loadPreferences() {
        val model = preferencesManager.getModelSync()
        _uiState.update {
            it.copy(
                host = preferencesManager.getHostSync(),
                port = preferencesManager.getPortSync().toString(),
                apiKey = preferencesManager.getApiKeySync(),
                modelName = model,
                systemPrompt = preferencesManager.getSystemPromptSync(model),
                isDarkMode = preferencesManager.getDarkModeSync(),
                isScreenshotsEnabled = preferencesManager.getScreenshotsEnabledSync(),
                themeMode = preferencesManager.getThemeModeSync(),
                isWebSearchEnabled = preferencesManager.getWebSearchEnabledSync()
            )
        }
    }

    fun updateDarkMode(isDark: Boolean?) {
        _uiState.update { it.copy(isDarkMode = isDark) }
        preferencesManager.updateDarkMode(isDark)
    }

    fun updateThemeMode(mode: String) {
        _uiState.update { it.copy(themeMode = mode) }
        preferencesManager.updateThemeMode(mode)
    }

    fun updateWebSearchEnabled(enabled: Boolean) {
        _uiState.update { it.copy(isWebSearchEnabled = enabled) }
        preferencesManager.updateWebSearchEnabled(enabled)
    }





    fun updateHost(host: String) {
        _uiState.update { it.copy(host = host) }
    }

    fun updatePort(port: String) {
        _uiState.update { it.copy(port = port) }
    }

    fun updateApiKey(apiKey: String) {
        _uiState.update { it.copy(apiKey = apiKey) }
    }

    fun updateModelName(modelName: String) {
        val promptForModel = preferencesManager.getSystemPromptSync(modelName)
        _uiState.update { 
            it.copy(
                modelName = modelName,
                systemPrompt = promptForModel
            ) 
        }
    }

    fun updateSystemPrompt(systemPrompt: String) {
        _uiState.update { it.copy(systemPrompt = systemPrompt) }
    }

    fun resetSystemPrompt() {
        _uiState.update { it.copy(systemPrompt = ServerPreferencesManager.DEFAULT_SYSTEM_PROMPT) }
    }

    fun testConnection() {
        fetchModels()
    }

    fun fetchModels() {
        val portInt = _uiState.value.port.toIntOrNull() ?: ServerPreferencesManager.DEFAULT_PORT
        // Ensure latest config is used for the fetch
        preferencesManager.updateServerConfig(
            host = _uiState.value.host,
            port = portInt,
            model = _uiState.value.modelName,
            systemPrompt = _uiState.value.systemPrompt,
            apiKey = _uiState.value.apiKey
        )

        _uiState.update { it.copy(connectionState = ConnectionState.Testing) }

        viewModelScope.launch {
            when (val result = repository.testConnection()) {
                is NetworkResult.Success -> {
                    val modelList = result.data
                    val firstLoadedModel = modelList.find { it.isLoaded }?.id
                    
                    _uiState.update {
                        it.copy(
                            connectionState = ConnectionState.Connected(modelCount = modelList.size),
                            availableModels = modelList,
                            modelName = if (firstLoadedModel != null && (it.modelName.isBlank() || it.modelName == ServerPreferencesManager.DEFAULT_MODEL)) {
                                firstLoadedModel
                            } else it.modelName,
                            isModelActionLoading = false
                        )
                    }
                }
                is NetworkResult.Error -> {
                    _uiState.update {
                        it.copy(
                            connectionState = ConnectionState.Error(result.message)
                        )
                    }
                }
                is NetworkResult.Loading -> {
                    _uiState.update { it.copy(connectionState = ConnectionState.Testing) }
                }
            }
        }
    }

    fun loadModel(modelId: String) {
        val model = _uiState.value.availableModels.find { it.id == modelId }
        val displayName = model?.displayName ?: modelId
        _uiState.update { it.copy(isModelActionLoading = true, modelActionMessage = "Now loading $displayName...") }
        viewModelScope.launch {
            when (val result = repository.loadModel(modelId)) {
                is NetworkResult.Success -> {
                    _uiState.update { it.copy(saveMessage = "Model loaded successfully!", modelActionMessage = null) }
                    fetchModels()
                }
                is NetworkResult.Error -> {
                    _uiState.update { it.copy(saveMessage = "Error loading model: ${result.message}", isModelActionLoading = false, modelActionMessage = null) }
                }
                else -> {
                    _uiState.update { it.copy(isModelActionLoading = false, modelActionMessage = null) }
                }
            }
        }
    }

    fun unloadModel(modelId: String) {
        val model = _uiState.value.availableModels.find { it.id == modelId || it.loadedInstances?.any { inst -> inst.id == modelId } == true }
        val displayName = model?.displayName ?: modelId
        _uiState.update { it.copy(isModelActionLoading = true, modelActionMessage = "Unloading $displayName...") }
        viewModelScope.launch {
            when (val result = repository.unloadModel(modelId)) {
                is NetworkResult.Success -> {
                    _uiState.update { it.copy(saveMessage = "Model unloaded successfully!", modelActionMessage = null) }
                    fetchModels()
                }
                is NetworkResult.Error -> {
                    _uiState.update { it.copy(saveMessage = "Error unloading model: ${result.message}", isModelActionLoading = false, modelActionMessage = null) }
                }
                else -> {
                    _uiState.update { it.copy(isModelActionLoading = false, modelActionMessage = null) }
                }
            }
        }
    }

    fun saveSettings() {
        val portInt = _uiState.value.port.toIntOrNull() ?: ServerPreferencesManager.DEFAULT_PORT
        preferencesManager.updateServerConfig(
            host = _uiState.value.host,
            port = portInt,
            model = _uiState.value.modelName,
            systemPrompt = _uiState.value.systemPrompt,
            apiKey = _uiState.value.apiKey
        )
        _uiState.update { it.copy(saveMessage = "Settings saved successfully!") }
    }

    fun clearSaveMessage() {
        _uiState.update { it.copy(saveMessage = null) }
    }

    fun updateScreenshotsEnabled(enabled: Boolean) {
        _uiState.update { it.copy(isScreenshotsEnabled = enabled) }
        preferencesManager.updateScreenshotsEnabled(enabled)
    }

    fun deleteModel(modelId: String) {
        val model = _uiState.value.availableModels.find { it.id == modelId }
        val displayName = model?.displayName ?: modelId
        _uiState.update { it.copy(isModelActionLoading = true, modelActionMessage = "Deleting $displayName...") }
        viewModelScope.launch {
            when (val result = repository.deleteModel(modelId)) {
                is NetworkResult.Success -> {
                    _uiState.update { it.copy(saveMessage = "Model deleted successfully!", modelActionMessage = null) }
                    // Update settings preference if the currently selected model was deleted
                    if (_uiState.value.modelName == modelId) {
                        updateModelName(ServerPreferencesManager.DEFAULT_MODEL)
                    }
                    fetchModels()
                }
                is NetworkResult.Error -> {
                    _uiState.update { it.copy(saveMessage = "Error deleting model: ${result.message}", isModelActionLoading = false, modelActionMessage = null) }
                }
                else -> {
                    _uiState.update { it.copy(isModelActionLoading = false, modelActionMessage = null) }
                }
            }
        }
    }

    class Factory(
        private val preferencesManager: ServerPreferencesManager,
        private val repository: ChatRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(preferencesManager, repository) as T
        }
    }
}
