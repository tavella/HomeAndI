package es.zombielawng.nom.homeandi.data.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ServerPreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("home_and_i_prefs", Context.MODE_PRIVATE)

    private val _hostFlow = MutableStateFlow(getHostSync())
    val hostFlow: Flow<String> = _hostFlow.asStateFlow()

    private val _portFlow = MutableStateFlow(getPortSync())
    val portFlow: Flow<Int> = _portFlow.asStateFlow()

    private val _modelFlow = MutableStateFlow(getModelSync())
    val modelFlow: Flow<String> = _modelFlow.asStateFlow()

    private val _apiKeyFlow = MutableStateFlow(getApiKeySync())
    val apiKeyFlow: Flow<String> = _apiKeyFlow.asStateFlow()

    private val _systemPromptFlow = MutableStateFlow(getSystemPromptSync())
    val systemPromptFlow: Flow<String> = _systemPromptFlow.asStateFlow()

    private val _isDarkModeFlow = MutableStateFlow(getDarkModeSync())
    val isDarkModeFlow: Flow<Boolean?> = _isDarkModeFlow.asStateFlow()

    fun getHostSync(): String {
        return prefs.getString(KEY_HOST, DEFAULT_HOST) ?: DEFAULT_HOST
    }

    fun getLastSessionIdSync(): String? {
        return prefs.getString(KEY_LAST_SESSION, null)
    }

    fun getPortSync(): Int {
        return prefs.getInt(KEY_PORT, DEFAULT_PORT)
    }

    fun getSchemeSync(): String {
        return prefs.getString(KEY_SCHEME, DEFAULT_SCHEME) ?: DEFAULT_SCHEME
    }

    fun getModelSync(): String {
        return prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
    }

    fun getApiKeySync(): String {
        return prefs.getString(KEY_API_KEY, DEFAULT_API_KEY) ?: DEFAULT_API_KEY
    }

    fun getSystemPromptSync(modelName: String = getModelSync()): String {
        val key = getSystemPromptKey(modelName)
        return prefs.getString(key, DEFAULT_SYSTEM_PROMPT) ?: DEFAULT_SYSTEM_PROMPT
    }

    private fun getSystemPromptKey(modelName: String): String {
        return "${KEY_SYSTEM_PROMPT}_${modelName.trim()}"
    }

    fun getDarkModeSync(): Boolean? {
        if (!prefs.contains(KEY_THEME_MODE)) return null
        return prefs.getBoolean(KEY_THEME_MODE, false)
    }

    fun getFullBaseUrlSync(): String {
        return "${getSchemeSync()}://${getHostSync()}:${getPortSync()}/"
    }

    fun updateServerConfig(host: String, port: Int, model: String, systemPrompt: String = getSystemPromptSync(model), apiKey: String = getApiKeySync()) {
        val cleanHost = host.trim().removePrefix("http://").removePrefix("https://")
        val cleanModel = model.trim()
        val keyForModel = getSystemPromptKey(cleanModel)
        prefs.edit()
            .putString(KEY_HOST, cleanHost)
            .putInt(KEY_PORT, port)
            .putString(KEY_MODEL, cleanModel)
            .putString(keyForModel, systemPrompt)
            .putString(KEY_SYSTEM_PROMPT, systemPrompt)
            .putString(KEY_API_KEY, apiKey.trim())
            .apply()

        _hostFlow.value = cleanHost
        _portFlow.value = port
        _modelFlow.value = cleanModel
        _systemPromptFlow.value = systemPrompt
        _apiKeyFlow.value = apiKey.trim()
    }

    fun updateLastSessionId(sessionId: String?) {
        if (sessionId == null) {
            prefs.edit().remove(KEY_LAST_SESSION).apply()
        } else {
            prefs.edit().putString(KEY_LAST_SESSION, sessionId).apply()
        }
    }

    fun updateDarkMode(isDark: Boolean?) {
        if (isDark == null) {
            prefs.edit().remove(KEY_THEME_MODE).apply()
        } else {
            prefs.edit().putBoolean(KEY_THEME_MODE, isDark).apply()
        }
        _isDarkModeFlow.value = isDark
    }

    companion object {
        const val KEY_HOST = "server_host"
        const val KEY_PORT = "server_port"
        const val KEY_SCHEME = "server_scheme"
        const val KEY_MODEL = "server_model"
        const val KEY_API_KEY = "server_api_key"
        const val KEY_SYSTEM_PROMPT = "system_prompt"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_LAST_SESSION = "last_session_id"

        const val DEFAULT_HOST = "10.0.2.2" // Emulator default targeting host machine
        const val DEFAULT_PORT = 8000
        const val DEFAULT_SCHEME = "http"
        const val DEFAULT_MODEL = "local-model"
        const val DEFAULT_API_KEY = ""
        const val DEFAULT_SYSTEM_PROMPT = "You are a precise, efficient, and direct AI assistant. Your goal is to provide accurate, well-structured, and actionable answers. Prioritize clarity over verbosity, avoid unnecessary conversational filler, and utilize Markdown (such as bullet points and code blocks) to organize information cleanly. When handling technical tasks, focus on clean implementation and practical utility."
    }
}
