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

        // 2. Fetch full contiguous history from Room DB and resolve configuration
        val session = chatSessionDao.getSessionById(sessionId)
        val model = session?.modelName ?: preferencesManager.getModelSync()

        // Strict boundary: Only allow web search when both settings switch and globe are active
        val isSearchAllowed = isWebSearchActive && preferencesManager.getWebSearchEnabledSync()

        var loopCount = 0
        val maxLoops = 5
        var finalMsg: ChatMessageEntity? = null

        try {
            while (loopCount < maxLoops) {
                loopCount++
                val historyEntities = chatMessageDao.getMessagesForSessionSync(sessionId)
                val apiMessages = buildApiMessages(historyEntities, model, isSearchAllowed)

                val request = ChatCompletionRequest(
                    model = model,
                    messages = apiMessages,
                    temperature = 0.7,
                    tools = if (isSearchAllowed) listOf(fetchWebDataTool) else null
                )

                val response = apiService.createChatCompletion(request)
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    val choice = body.choices?.firstOrNull()
                    val responseMsg = choice?.message
                    val assistantContent = responseMsg?.content ?: ""
                    val toolCalls = responseMsg?.toolCalls

                    if (!toolCalls.isNullOrEmpty()) {
                        // Insert the assistant message with tool calls
                        val assistantMsgId = UUID.randomUUID().toString()
                        val assistantMsg = ChatMessageEntity(
                            id = assistantMsgId,
                            sessionId = sessionId,
                            role = "assistant",
                            content = assistantContent,
                            timestamp = System.currentTimeMillis(),
                            status = "SENT",
                            toolCallsJson = gson.toJson(toolCalls)
                        )
                        chatMessageDao.insertMessage(assistantMsg)

                        // Process each tool call
                        for (toolCall in toolCalls) {
                            if (toolCall.function.name == "fetch_web_data") {
                                val argsStr = toolCall.function.arguments
                                val url = parseUrlFromArgs(argsStr)
                                val toolResultText = if (!url.isNullOrBlank()) {
                                    executeFetchWebData(url)
                                } else {
                                    "Error: URL not found or invalid format in tool arguments."
                                }

                                // Insert the tool result message
                                val toolMsgId = UUID.randomUUID().toString()
                                val toolMsg = ChatMessageEntity(
                                    id = toolMsgId,
                                    sessionId = sessionId,
                                    role = "tool",
                                    content = toolResultText,
                                    timestamp = System.currentTimeMillis(),
                                    status = "SENT",
                                    toolCallId = toolCall.id,
                                    name = "fetch_web_data"
                                )
                                chatMessageDao.insertMessage(toolMsg)
                            }
                        }
                    } else {
                        // Final text response
                        val assistantMsgId = UUID.randomUUID().toString()
                        val assistantMsg = ChatMessageEntity(
                            id = assistantMsgId,
                            sessionId = sessionId,
                            role = "assistant",
                            content = assistantContent,
                            timestamp = System.currentTimeMillis(),
                            status = "SENT"
                        )
                        chatMessageDao.insertMessage(assistantMsg)
                        finalMsg = assistantMsg
                        break
                    }
                } else {
                    val errorMsg = "HTTP ${response.code()}: ${response.errorBody()?.string() ?: response.message()}"
                    chatMessageDao.updateMessageStatus(userMsgId, status = "ERROR")
                    return@withContext NetworkResult.Error(errorMsg)
                }
            }

            if (finalMsg != null) {
                NetworkResult.Success(finalMsg)
            } else {
                chatMessageDao.updateMessageStatus(userMsgId, status = "ERROR")
                NetworkResult.Error("Local model tool call loop reached max iterations ($maxLoops) without final response.")
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            chatMessageDao.updateMessageStatus(userMsgId, status = "ERROR")
            throw e
        } catch (e: Exception) {
            chatMessageDao.updateMessageStatus(userMsgId, status = "ERROR")
            NetworkResult.Error("Network error: ${e.localizedMessage ?: "Connection failed"}", e)
        }
    }

    private val fetchWebDataTool = ChatToolDefinition(
        type = "function",
        function = ChatFunctionDefinition(
            name = "fetch_web_data",
            description = "Fetches the raw text content or HTML from a given URL to retrieve up-to-date information.",
            parameters = ChatFunctionParameters(
                type = "object",
                properties = mapOf(
                    "url" to ChatFunctionProperty(
                        type = "string",
                        description = "The absolute URL to fetch (e.g., https://example.com/api/data or https://news.ycombinator.com)."
                    )
                ),
                required = listOf("url")
            )
        )
    )

    private val nativeHttpClient = okhttp3.OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private suspend fun executeFetchWebData(urlStr: String): String = withContext(Dispatchers.IO) {
        try {
            val request = okhttp3.Request.Builder()
                .url(urlStr)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:100.0) Gecko/100.0 Firefox/100.0")
                .build()
            nativeHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val cleanText = cleanHtml(body)
                    if (cleanText.length > 8000) {
                        cleanText.take(8000) + "\n... [Content Truncated] ..."
                    } else {
                        cleanText
                    }
                } else {
                    "Error: HTTP ${response.code} trying to fetch $urlStr"
                }
            }
        } catch (e: Exception) {
            "Error fetching URL: ${e.localizedMessage ?: e.message ?: "Unknown error"}"
        }
    }

    private fun cleanHtml(html: String): String {
        var text = html.replace(Regex("<script[\\s\\S]*?>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
        text = text.replace(Regex("<style[\\s\\S]*?>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
        text = text.replace(Regex("<[\\s\\S]*?>"), " ")
        text = text.replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
        
        val lines = text.split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return lines.joinToString("\n")
    }

    private fun parseUrlFromArgs(argsString: String): String? {
        try {
            val jsonObject = gson.fromJson(argsString, com.google.gson.JsonObject::class.java)
            if (jsonObject.has("url")) {
                return jsonObject.get("url").asString
            }
        } catch (e: Exception) {
            val pattern = java.util.regex.Pattern.compile("https?://\\S+")
            val matcher = pattern.matcher(argsString)
            if (matcher.find()) {
                var url = matcher.group()
                url = url.trim { it == '"' || it == '}' || it == ']' || it == ' ' || it == '\'' || it == ',' }
                return url
            }
        }
        return null
    }

    private fun buildApiMessages(
        historyEntities: List<ChatMessageEntity>,
        model: String,
        isSearchAllowed: Boolean
    ): List<ApiMessage> {
        val apiMessages = mutableListOf<ApiMessage>()
        val baseSystemPrompt = preferencesManager.getSystemPromptSync(model)
        val systemPrompt = if (isSearchAllowed) {
            val toolInstruction = """
                You have access to a tool named `fetch_web_data`.
                You MUST use it when the user asks for up-to-date information, search queries, real-time facts, news, or the contents of a specific web URL.
                To retrieve web data, call the `fetch_web_data` tool with the absolute target URL.
                Once you receive the content from the tool, synthesize the information and answer the user's question accurately. Do not cite fake sources.
            """.trimIndent()
            if (baseSystemPrompt.isBlank()) {
                toolInstruction
            } else {
                "$baseSystemPrompt\n\n$toolInstruction"
            }
        } else {
            baseSystemPrompt
        }

        if (systemPrompt.isNotBlank()) {
            apiMessages.add(ApiMessage(role = "system", content = systemPrompt))
        }

        for (entity in historyEntities) {
            val contentToSend = entity.content
            val parts = parseAttachments(entity.attachmentPathsJson)
            
            val toolCalls = if (!entity.toolCallsJson.isNullOrBlank()) {
                try {
                    val type = object : TypeToken<List<ChatToolCall>>() {}.type
                    gson.fromJson<List<ChatToolCall>>(entity.toolCallsJson, type)
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }

            if (parts.isNotEmpty() && entity.role == "user") {
                val contentParts = mutableListOf<Any>()
                if (contentToSend.isNotBlank()) {
                    contentParts.add(TextContentPart(text = contentToSend))
                }
                for (path in parts) {
                    val dataUrl = ImageUtils.pathToBase64DataUrl(context, path)
                    contentParts.add(ImageUrlContentPart(imageUrl = ImageUrlDetail(url = dataUrl)))
                }
                apiMessages.add(
                    ApiMessage(
                        role = entity.role,
                        content = contentParts,
                        toolCallId = entity.toolCallId,
                        name = entity.name,
                        toolCalls = toolCalls
                    )
                )
            } else {
                apiMessages.add(
                    ApiMessage(
                        role = entity.role,
                        content = contentToSend,
                        toolCallId = entity.toolCallId,
                        name = entity.name,
                        toolCalls = toolCalls
                    )
                )
            }
        }
        return apiMessages
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


    private fun parseAttachments(json: String): List<String> {
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }


}
