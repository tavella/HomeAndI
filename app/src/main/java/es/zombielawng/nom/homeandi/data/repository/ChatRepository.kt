package es.zombielawng.nom.homeandi.data.repository

import android.content.Context
import es.zombielawng.nom.homeandi.data.local.ChatMessageDao
import es.zombielawng.nom.homeandi.data.local.ChatMessageEntity
import es.zombielawng.nom.homeandi.data.local.ChatSessionDao
import es.zombielawng.nom.homeandi.data.local.ChatSessionEntity
import es.zombielawng.nom.homeandi.data.preferences.ServerPreferencesManager
import es.zombielawng.nom.homeandi.data.remote.*
import es.zombielawng.nom.homeandi.util.ImageUtils
import es.zombielawng.nom.homeandi.util.NetworkResult
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import android.util.Base64
import java.util.UUID

open class ChatRepository(
    private val chatSessionDao: ChatSessionDao,
    private val chatMessageDao: ChatMessageDao,
    private val apiService: HomeAndIApiService,
    private val preferencesManager: ServerPreferencesManager,
    private val context: Context? = null
) {
    private val gson = Gson()
    private var isOmlxBackend: Boolean = false

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
        attachmentPaths: List<String> = emptyList(),
        isWebSearchActive: Boolean = false
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

        val session = chatSessionDao.getSessionById(sessionId)
        val model = session?.modelName ?: preferencesManager.getModelSync()

        val systemPrompt = preferencesManager.getSystemPromptSync(model)
        if (systemPrompt.isNotBlank()) {
            apiMessages.add(ApiMessage(role = "system", content = systemPrompt))
        }

        val lastIdx = historyEntities.lastIndex
        for (i in historyEntities.indices) {
            val entity = historyEntities[i]
            val contentToSend = entity.content

            val parts = parseAttachments(entity.attachmentPathsJson)
            if (parts.isNotEmpty() && entity.role == "user") {
                val contentParts = mutableListOf<Any>()
                if (contentToSend is String && contentToSend.isNotBlank()) {
                    contentParts.add(TextContentPart(text = contentToSend))
                }
                for (path in parts) {
                    val dataUrl = ImageUtils.pathToBase64DataUrl(context, path)
                    contentParts.add(ImageUrlContentPart(imageUrl = ImageUrlDetail(url = dataUrl)))
                }
                apiMessages.add(ApiMessage(role = entity.role, content = contentParts))
            } else {
                apiMessages.add(ApiMessage(role = entity.role, content = contentToSend))
            }
        }

        val isGeminiModel = model.startsWith("gemini", ignoreCase = true)
        if (isWebSearchActive || isGeminiModel) {
            val geminiApiKey = preferencesManager.getGeminiApiKeySync()
            val geminiContents = convertToGeminiContents(apiMessages)
            
            val tools = if (isWebSearchActive) {
                listOf(GeminiTool(googleSearch = emptyMap()))
            } else {
                null
            }

            val geminiRequest = GeminiGenerateContentRequest(
                contents = geminiContents,
                tools = tools,
                generationConfig = GeminiGenerationConfig(temperature = 0.7),
                systemInstruction = if (systemPrompt.isNotBlank()) {
                    GeminiContent(
                        role = "system",
                        parts = listOf(GeminiPart(text = systemPrompt))
                    )
                } else null
            )

            // Since gemini-1.5-flash is not supported on this specific key, use gemini-2.5-flash as the default fallback.
            val targetModel = if (isGeminiModel) {
                if (model == "gemini-1.5-flash") "gemini-2.5-flash" else model
            } else {
                "gemini-2.5-flash"
            }

            try {
                val response = apiService.generateContent(
                    model = targetModel,
                    apiKey = geminiApiKey,
                    request = geminiRequest
                )

                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    val candidate = body.candidates?.firstOrNull()
                    val assistantReplyText = candidate?.content?.parts?.firstOrNull()?.text
                        ?: "No content returned by Gemini."
                    val groundingMetadata = candidate?.groundingMetadata
                    val groundingMetadataJson = if (groundingMetadata != null) {
                        gson.toJson(groundingMetadata)
                    } else {
                        null
                    }

                    val assistantMsg = ChatMessageEntity(
                        id = UUID.randomUUID().toString(),
                        sessionId = sessionId,
                        role = "assistant",
                        content = assistantReplyText,
                        timestamp = System.currentTimeMillis(),
                        status = "SENT",
                        groundingMetadataJson = groundingMetadataJson
                    )
                    chatMessageDao.insertMessage(assistantMsg)
                    NetworkResult.Success(assistantMsg)
                } else {
                    val errorMsg = "HTTP ${response.code()}: ${response.errorBody()?.string() ?: response.message()}"
                    chatMessageDao.updateMessageStatus(userMsgId, status = "ERROR")
                    NetworkResult.Error(errorMsg)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                chatMessageDao.updateMessageStatus(userMsgId, status = "ERROR")
                throw e
            } catch (e: Exception) {
                chatMessageDao.updateMessageStatus(userMsgId, status = "ERROR")
                NetworkResult.Error("Network error: ${e.localizedMessage ?: "Connection failed"}", e)
            }
        } else {
            val request = ChatCompletionRequest(
                model = model,
                messages = apiMessages,
                temperature = 0.7,
                tools = null
            )

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
                        status = "SENT",
                        groundingMetadataJson = null
                    )
                    chatMessageDao.insertMessage(assistantMsg)
                    NetworkResult.Success(assistantMsg)
                } else {
                    val errorMsg = "HTTP ${response.code()}: ${response.errorBody()?.string() ?: response.message()}"
                    chatMessageDao.updateMessageStatus(userMsgId, status = "ERROR")
                    NetworkResult.Error(errorMsg)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                chatMessageDao.updateMessageStatus(userMsgId, status = "ERROR")
                throw e
            } catch (e: Exception) {
                chatMessageDao.updateMessageStatus(userMsgId, status = "ERROR")
                NetworkResult.Error("Network error: ${e.localizedMessage ?: "Connection failed"}", e)
            }
        }
    }

    private fun convertToGeminiContents(apiMessages: List<ApiMessage>): List<GeminiContent> {
        return apiMessages.filter { it.role != "system" }.map { msg ->
            val role = when (msg.role) {
                "user" -> "user"
                "assistant" -> "model"
                else -> "user"
            }
            val parts = when (val content = msg.content) {
                is String -> listOf(GeminiPart(text = content))
                is List<*> -> {
                    content.mapNotNull { part ->
                        when (part) {
                            is TextContentPart -> GeminiPart(text = part.text)
                            is ImageUrlContentPart -> {
                                val base64Data = part.imageUrl.url.substringAfter("base64,")
                                val mimeType = part.imageUrl.url.substringBefore(";base64,").substringAfter("data:")
                                GeminiPart(inlineData = GeminiInlineData(mimeType = mimeType, data = base64Data))
                            }
                            else -> null
                        }
                    }
                }
                else -> listOf(GeminiPart(text = content.toString()))
            }
            GeminiContent(role = role, parts = parts)
        }
    }



    /**
     * Executes ping/connectivity test against HomeAndI server (/v1/models).
     */
    private fun parseOmlxLoadedModelIds(jsonElement: com.google.gson.JsonElement?): Set<String> {
        val loadedIds = mutableSetOf<String>()
        if (jsonElement == null) return loadedIds
        try {
            if (jsonElement.isJsonObject) {
                val rootObj = jsonElement.asJsonObject
                val modelsArray = when {
                    rootObj.has("models") && rootObj.get("models").isJsonArray -> rootObj.getAsJsonArray("models")
                    rootObj.has("data") && rootObj.get("data").isJsonArray -> rootObj.getAsJsonArray("data")
                    else -> null
                }
                if (modelsArray != null) {
                    for (element in modelsArray) {
                        if (element.isJsonObject) {
                            val obj = element.asJsonObject
                            val id = obj.get("id")?.asString
                            val loaded = obj.get("loaded")?.asBoolean ?: false
                            if (id != null && loaded) {
                                loadedIds.add(id)
                            }
                        }
                    }
                } else {
                    val id = rootObj.get("id")?.asString
                    val loaded = rootObj.get("loaded")?.asBoolean ?: false
                    if (id != null && loaded) {
                        loadedIds.add(id)
                    }
                }
            } else if (jsonElement.isJsonArray) {
                val array = jsonElement.asJsonArray
                for (element in array) {
                    if (element.isJsonObject) {
                        val obj = element.asJsonObject
                        val id = obj.get("id")?.asString
                        val loaded = obj.get("loaded")?.asBoolean ?: false
                        if (id != null && loaded) {
                            loadedIds.add(id)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore parse errors
        }
        return loadedIds
    }

    suspend fun testConnection(): NetworkResult<List<ModelData>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getModels()
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()
                isOmlxBackend = body?.data != null && body.models == null
                val rawModels = body?.models ?: body?.data ?: emptyList()

                val models = if (isOmlxBackend) {
                    val loadedIds = try {
                        val statusResponse = apiService.getModelsStatus()
                        if (statusResponse.isSuccessful) {
                            parseOmlxLoadedModelIds(statusResponse.body())
                        } else emptySet()
                    } catch (e: Exception) {
                        emptySet()
                    }

                    rawModels.map { model ->
                        model.copy(_isOmlxLoaded = loadedIds.contains(model.id))
                    }
                } else {
                    rawModels
                }

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
            val response = if (isOmlxBackend) {
                apiService.loadModelOmlx(modelId)
            } else {
                apiService.loadModelLmStudio(ModelLoadRequest(modelId))
            }
            if (response.isSuccessful) {
                NetworkResult.Success(Unit)
            } else {
                val errorBodyStr = response.errorBody()?.string()
                val parsedErrorMsg = parseErrorMessage(errorBodyStr, response.code())
                NetworkResult.Error(parsedErrorMsg)
            }
        } catch (e: Exception) {
            NetworkResult.Error("Error loading model: ${e.localizedMessage}")
        }
    }

    private fun parseErrorMessage(errorBody: String?, responseCode: Int): String {
        if (errorBody.isNullOrBlank()) {
            return "Server error (HTTP $responseCode)"
        }
        
        try {
            val jsonObject = gson.fromJson(errorBody, com.google.gson.JsonObject::class.java)
            if (jsonObject != null) {
                if (jsonObject.has("error")) {
                    val errorElem = jsonObject.get("error")
                    if (errorElem.isJsonObject) {
                        val errorObj = errorElem.asJsonObject
                        if (errorObj.has("message")) {
                            val msg = errorObj.get("message").asString
                            if (msg.isNotBlank()) return formatUserFriendlyError(msg, responseCode)
                        }
                    } else if (errorElem.isJsonPrimitive) {
                        val msg = errorElem.asString
                        if (msg.isNotBlank()) return formatUserFriendlyError(msg, responseCode)
                    }
                }
                if (jsonObject.has("message")) {
                    val msg = jsonObject.get("message").asString
                    if (msg.isNotBlank()) return formatUserFriendlyError(msg, responseCode)
                }
            }
        } catch (e: Exception) {
            // Not a JSON object
        }
        
        return formatUserFriendlyError(errorBody, responseCode)
    }

    private fun formatUserFriendlyError(rawMessage: String, responseCode: Int): String {
        val lowerMessage = rawMessage.lowercase()
        val isResourceIssue = lowerMessage.contains("memory") ||
                lowerMessage.contains("allocate") ||
                lowerMessage.contains("allocation") ||
                lowerMessage.contains("resource") ||
                lowerMessage.contains("vram") ||
                lowerMessage.contains("ram") ||
                lowerMessage.contains("gpu") ||
                lowerMessage.contains("oom") ||
                lowerMessage.contains("capacity")

        if (isResourceIssue) {
            return "Not enough resources to load the model. The model exceeds the available VRAM or RAM on the server.\n\n" +
                    "To fix this, try:\n" +
                    "• Choosing a smaller quantization (e.g., Q4_K_M or Q3_K_S)\n" +
                    "• Reducing the context length or GPU offload layers\n" +
                    "• Unloading any currently loaded models first"
        }

        val cleanMsg = if (rawMessage.length > 200) rawMessage.take(200) + "..." else rawMessage
        return "Failed to load model: $cleanMsg"
    }

    suspend fun unloadModel(instanceId: String): NetworkResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = if (isOmlxBackend) {
                apiService.unloadModelOmlx(instanceId)
            } else {
                apiService.unloadModelLmStudio(ModelUnloadRequest(instanceId))
            }
            if (response.isSuccessful) {
                NetworkResult.Success(Unit)
            } else {
                NetworkResult.Error("Failed to unload model: ${response.code()}")
            }
        } catch (e: Exception) {
            NetworkResult.Error("Error unloading model: ${e.localizedMessage}")
        }
    }

    suspend fun deleteModel(modelId: String): NetworkResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = if (isOmlxBackend) {
                apiService.deleteModelOmlx(modelId)
            } else {
                apiService.deleteModel(modelId)
            }
            if (response.isSuccessful) {
                NetworkResult.Success(Unit)
            } else {
                NetworkResult.Error("Failed to delete model: ${response.code()}")
            }
        } catch (e: Exception) {
            NetworkResult.Error("Error deleting model: ${e.localizedMessage}")
        }
    }

    suspend fun fetchGeminiModels(): NetworkResult<List<String>> = withContext(Dispatchers.IO) {
        try {
            val geminiApiKey = preferencesManager.getGeminiApiKeySync()
            if (geminiApiKey.isBlank()) {
                return@withContext NetworkResult.Error("Gemini API key is not configured.")
            }
            val response = apiService.getGeminiModels(geminiApiKey)
            if (response.isSuccessful && response.body() != null) {
                val modelsList = response.body()!!.models ?: emptyList()
                val names = modelsList
                    .filter { it.supportedGenerationMethods?.contains("generateContent") == true }
                    .map { it.name.substringAfter("models/") }
                NetworkResult.Success(names)
            } else {
                NetworkResult.Error("HTTP ${response.code()}: ${response.errorBody()?.string() ?: response.message()}")
            }
        } catch (e: Exception) {
            NetworkResult.Error("Error fetching Gemini models: ${e.localizedMessage}")
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
