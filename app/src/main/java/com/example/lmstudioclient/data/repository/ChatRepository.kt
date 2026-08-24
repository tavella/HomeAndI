package com.example.lmstudioclient.data.repository

import android.content.Context
import com.example.lmstudioclient.data.local.ChatMessageDao
import com.example.lmstudioclient.data.local.ChatMessageEntity
import com.example.lmstudioclient.data.local.ChatSessionDao
import com.example.lmstudioclient.data.local.ChatSessionEntity
import com.example.lmstudioclient.data.preferences.ServerPreferencesManager
import com.example.lmstudioclient.data.remote.*
import com.example.lmstudioclient.util.ImageUtils
import com.example.lmstudioclient.util.NetworkResult
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

class ChatRepository(
    private val chatSessionDao: ChatSessionDao,
    private val chatMessageDao: ChatMessageDao,
    private val apiService: LMStudioApiService,
    private val preferencesManager: ServerPreferencesManager,
    private val context: Context? = null
) {
    private val gson = Gson()

    fun getAllSessions(): Flow<List<ChatSessionEntity>> = chatSessionDao.getAllSessions()

    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessageEntity>> =
        chatMessageDao.getMessagesForSession(sessionId)

    suspend fun createNewSession(
        title: String = "New Chat",
        modelName: String = preferencesManager.getModelSync()
    ): ChatSessionEntity = withContext(Dispatchers.IO) {
        val newSession = ChatSessionEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            modelName = modelName,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        chatSessionDao.insertSession(newSession)
        newSession
    }

    suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        val session = chatSessionDao.getSessionById(sessionId)
        if (session != null) {
            chatSessionDao.deleteSession(session)
        }
    }

    suspend fun updateSession(session: ChatSessionEntity) = withContext(Dispatchers.IO) {
        chatSessionDao.updateSession(session)
    }

    /**
     * Core Conversation Continuity logic:
     * 1. Persists user prompt message into Room.
     * 2. Fetches entire ordered message history for the session from Room.
     * 3. Constructs the contiguous ApiMessage array (including multimodal payload formatting).
     * 4. Sends request to HomeAndI server (/v1/chat/completions).
     * 5. Persists assistant reply in Room.
     */
    suspend fun sendMessage(
        sessionId: String,
        userPrompt: String,
        attachmentPaths: List<String> = emptyList()
    ): NetworkResult<ChatMessageEntity> = withContext(Dispatchers.IO) {
        val userMsgId = UUID.randomUUID().toString()
        val attachmentJson = gson.toJson(attachmentPaths)
        val userMessage = ChatMessageEntity(
            id = userMsgId,
            sessionId = sessionId,
            role = "user",
            content = userPrompt,
            attachmentPathsJson = attachmentJson,
            timestamp = System.currentTimeMillis(),
            status = "SENT"
        )

        // 1. Insert user message into Room DB
        chatMessageDao.insertMessage(userMessage)

        // Update session updatedAt timestamp
        chatSessionDao.getSessionById(sessionId)?.let { session ->
            val updatedTitle = if (session.title == "New Chat" && userPrompt.isNotBlank()) {
                userPrompt.take(30)
            } else session.title
            chatSessionDao.updateSession(
                session.copy(
                    title = updatedTitle,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }

        // 2. Fetch full contiguous history from Room DB
        val historyEntities = chatMessageDao.getMessagesForSessionSync(sessionId)

        // 3. Build API message list (System prompt + historical context)
        val apiMessages = mutableListOf<ApiMessage>()

        val systemPrompt = preferencesManager.getSystemPromptSync()
        if (systemPrompt.isNotBlank()) {
            apiMessages.add(ApiMessage(role = "system", content = systemPrompt))
        }

        for (entity in historyEntities) {
            val parts = parseAttachments(entity.attachmentPathsJson)
            if (parts.isNotEmpty() && entity.role == "user") {
                // Multimodal input formatting
                val contentParts = mutableListOf<Any>()
                if (entity.content.isNotBlank()) {
                    contentParts.add(TextContentPart(text = entity.content))
                }
                for (path in parts) {
                    val dataUrl = ImageUtils.pathToBase64DataUrl(context, path)
                    contentParts.add(ImageUrlContentPart(imageUrl = ImageUrlDetail(url = dataUrl)))
                }
                apiMessages.add(ApiMessage(role = entity.role, content = contentParts))
            } else {
                // Standard text message
                apiMessages.add(ApiMessage(role = entity.role, content = entity.content))
            }
        }

        // 4. Construct Retrofit Payload
        val model = preferencesManager.getModelSync()
        val request = ChatCompletionRequest(
            model = model,
            messages = apiMessages,
            temperature = 0.7
        )

        // 5. Execute Network Call
        try {
            val response = apiService.createChatCompletion(request)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val assistantReplyText = body.choices?.firstOrNull()?.message?.content
                    ?: "No content returned by HomeAndI."

                val assistantMsg = ChatMessageEntity(
                    id = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    role = "assistant",
                    content = assistantReplyText,
                    timestamp = System.currentTimeMillis(),
                    status = "SENT"
                )
                chatMessageDao.insertMessage(assistantMsg)
                NetworkResult.Success(assistantMsg)
            } else {
                val errorMsg = "HTTP ${response.code()}: ${response.errorBody()?.string() ?: response.message()}"
                chatMessageDao.updateMessageStatus(userMsgId, status = "ERROR")
                NetworkResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            chatMessageDao.updateMessageStatus(userMsgId, status = "ERROR")
            NetworkResult.Error("Network error: ${e.localizedMessage ?: "Connection failed"}", e)
        }
    }

    /**
     * Executes ping/connectivity test against HomeAndI server (/v1/models).
     */
    suspend fun testConnection(): NetworkResult<List<ModelData>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getModels()
            if (response.isSuccessful && response.body() != null) {
                val models = response.body()?.models ?: emptyList()
                NetworkResult.Success(models)
            } else {
                NetworkResult.Error("Server returned code ${response.code()}")
            }
        } catch (e: Exception) {
            NetworkResult.Error("Failed to reach server: ${e.localizedMessage ?: "Host unreachable"}", e)
        }
    }

    suspend fun loadModel(modelId: String): NetworkResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.loadModel(ModelLoadRequest(modelId))
            if (response.isSuccessful) {
                NetworkResult.Success(Unit)
            } else {
                NetworkResult.Error("Failed to load model: ${response.code()}")
            }
        } catch (e: Exception) {
            NetworkResult.Error("Error loading model: ${e.localizedMessage}")
        }
    }

    suspend fun unloadModel(instanceId: String): NetworkResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.unloadModel(ModelUnloadRequest(instanceId))
            if (response.isSuccessful) {
                NetworkResult.Success(Unit)
            } else {
                NetworkResult.Error("Failed to unload model: ${response.code()}")
            }
        } catch (e: Exception) {
            NetworkResult.Error("Error unloading model: ${e.localizedMessage}")
        }
    }

    private fun parseAttachments(json: String): List<String> {
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
