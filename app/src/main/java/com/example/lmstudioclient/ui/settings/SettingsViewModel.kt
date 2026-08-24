package com.example.lmstudioclient.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.lmstudioclient.data.preferences.ServerPreferencesManager
import com.example.lmstudioclient.data.repository.ChatRepository
import com.example.lmstudioclient.ui.components.ConnectionState
import com.example.lmstudioclient.util.NetworkResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val host: String = ServerPreferencesManager.DEFAULT_HOST,
    val port: String = ServerPreferencesManager.DEFAULT_PORT.toString(),
    val modelName: String = ServerPreferencesManager.DEFAULT_MODEL,
    val systemPrompt: String = ServerPreferencesManager.DEFAULT_SYSTEM_PROMPT,
    val isDarkMode: Boolean? = null, // null means follow system
    val connectionState: ConnectionState = ConnectionState.Idle,
    val availableModels: List<String> = emptyList(),
    val saveMessage: String? = null
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
        _uiState.update {
            it.copy(
                host = preferencesManager.getHostSync(),
                port = preferencesManager.getPortSync().toString(),
                modelName = preferencesManager.getModelSync(),
                systemPrompt = preferencesManager.getSystemPromptSync(),
                isDarkMode = preferencesManager.getDarkModeSync()
            )
        }
    }

    fun updateDarkMode(isDark: Boolean?) {
        _uiState.update { it.copy(isDarkMode = isDark) }
        preferencesManager.updateDarkMode(isDark)
    }

    fun updateHost(host: String) {
        _uiState.update { it.copy(host = host) }
    }

    fun updatePort(port: String) {
        _uiState.update { it.copy(port = port) }
    }

    fun updateModelName(modelName: String) {
        _uiState.update { it.copy(modelName = modelName) }
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
            systemPrompt = _uiState.value.systemPrompt
        )

        _uiState.update { it.copy(connectionState = ConnectionState.Testing) }

        viewModelScope.launch {
            when (val result = repository.testConnection()) {
                is NetworkResult.Success -> {
                    val modelList = result.data.map { it.id }
                    _uiState.update {
                        it.copy(
                            connectionState = ConnectionState.Connected(modelCount = modelList.size),
                            availableModels = modelList,
                            modelName = if (modelList.isNotEmpty() && (it.modelName.isBlank() || it.modelName == ServerPreferencesManager.DEFAULT_MODEL)) {
                                modelList.first()
                            } else it.modelName
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

    fun saveSettings() {
        val portInt = _uiState.value.port.toIntOrNull() ?: ServerPreferencesManager.DEFAULT_PORT
        preferencesManager.updateServerConfig(
            host = _uiState.value.host,
            port = portInt,
            model = _uiState.value.modelName,
            systemPrompt = _uiState.value.systemPrompt
        )
        _uiState.update { it.copy(saveMessage = "Settings saved successfully!") }
    }

    fun clearSaveMessage() {
        _uiState.update { it.copy(saveMessage = null) }
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
