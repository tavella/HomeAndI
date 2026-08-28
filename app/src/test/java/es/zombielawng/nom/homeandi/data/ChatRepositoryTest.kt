package es.zombielawng.nom.homeandi.data

import es.zombielawng.nom.homeandi.data.local.ChatMessageDao
import es.zombielawng.nom.homeandi.data.local.ChatMessageEntity
import es.zombielawng.nom.homeandi.data.local.ChatSessionDao
import es.zombielawng.nom.homeandi.data.local.ChatSessionEntity
import es.zombielawng.nom.homeandi.data.preferences.ServerPreferencesManager
import es.zombielawng.nom.homeandi.data.remote.*
import es.zombielawng.nom.homeandi.data.repository.ChatRepository
import es.zombielawng.nom.homeandi.util.NetworkResult
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class ChatRepositoryTest {

    private val chatSessionDao: ChatSessionDao = mockk(relaxed = true)
    private val chatMessageDao: ChatMessageDao = mockk(relaxed = true)
    private val apiService: HomeAndIApiService = mockk()
    private val preferencesManager: ServerPreferencesManager = mockk(relaxed = true)
    private val context: android.content.Context = mockk(relaxed = true)

    private lateinit var repository: ChatRepository

    @Before
    fun setUp() {
        every { preferencesManager.getModelSync() } returns "meta-llama-3-8b"
        every { preferencesManager.getSystemPromptSync(any()) } returns "You are a helpful assistant."

        repository = spyk(ChatRepository(
            chatSessionDao = chatSessionDao,
            chatMessageDao = chatMessageDao,
            apiService = apiService,
            preferencesManager = preferencesManager,
            context = context
        ))
    }

    @Test
    fun `sendMessage fetches history and sends correct prompt array to Gemini REST API`() = runTest {
        val sessionId = "session-123"
        val existingSession = ChatSessionEntity(id = sessionId, title = "New Chat", modelName = "gemini-1.5-flash")
        val oldUserMsg = ChatMessageEntity(id = "msg-1", sessionId = sessionId, role = "user", content = "Hello")
        val oldAssistantMsg = ChatMessageEntity(id = "msg-2", sessionId = sessionId, role = "assistant", content = "Hi there!")

        coEvery { chatSessionDao.getSessionById(sessionId) } returns existingSession
        val newUserMsg = ChatMessageEntity(id = "msg-3", sessionId = sessionId, role = "user", content = "How are you?")
        coEvery { chatMessageDao.getMessagesForSessionSync(sessionId) } returns listOf(oldUserMsg, oldAssistantMsg, newUserMsg)
        every { preferencesManager.getGeminiApiKeySync() } returns "AIzaSyFakeKey"

        val mockCandidate = GeminiCandidate(
            content = GeminiContent(role = "model", parts = listOf(GeminiPart(text = "I am doing great"))),
            finishReason = "STOP",
            groundingMetadata = null
        )
        val mockResponse = GeminiGenerateContentResponse(
            candidates = listOf(mockCandidate),
            usageMetadata = null
        )

        val requestSlot = slot<GeminiGenerateContentRequest>()
        coEvery { apiService.generateContent(
            model = "gemini-1.5-flash",
            apiKey = "AIzaSyFakeKey",
            request = capture(requestSlot)
        ) } returns Response.success(mockResponse)

        val result = repository.sendMessage(sessionId = sessionId, userPrompt = "How are you?", isWebSearchActive = true)

        assertTrue(result is NetworkResult.Success)
        val assistantMsg = (result as NetworkResult.Success).data
        assertEquals("I am doing great", assistantMsg.content)
        assertEquals("assistant", assistantMsg.role)

        val capturedRequest = requestSlot.captured
        assertEquals(3, capturedRequest.contents.size)
    }

    @Test
    fun `sendMessage with search disabled routes to local OpenAI completion`() = runTest {
        val sessionId = "session-123"
        val existingSession = ChatSessionEntity(id = sessionId, title = "New Chat", modelName = "meta-llama-3-8b")
        val oldUserMsg = ChatMessageEntity(id = "msg-1", sessionId = sessionId, role = "user", content = "Hello")
        val oldAssistantMsg = ChatMessageEntity(id = "msg-2", sessionId = sessionId, role = "assistant", content = "Hi there!")

        coEvery { chatSessionDao.getSessionById(sessionId) } returns existingSession
        val newUserMsg = ChatMessageEntity(id = "msg-3", sessionId = sessionId, role = "user", content = "How are you?")
        coEvery { chatMessageDao.getMessagesForSessionSync(sessionId) } returns listOf(oldUserMsg, oldAssistantMsg, newUserMsg)

        val requestSlot = slot<ChatCompletionRequest>()
        val mockApiResponse = ChatCompletionResponse(
            id = "resp-1",
            created = 1000L,
            model = "meta-llama-3-8b",
            choices = listOf(
                Choice(
                    index = 0,
                    message = ResponseMessage(role = "assistant", content = "I am doing great, how can I help?"),
                    finishReason = "stop"
                )
            ),
            usage = Usage(10, 15, 25)
        )

        coEvery { apiService.createChatCompletion(capture(requestSlot)) } returns Response.success(mockApiResponse)

        val result = repository.sendMessage(sessionId = sessionId, userPrompt = "How are you?", isWebSearchActive = false)

        assertTrue(result is NetworkResult.Success)
        val assistantMsg = (result as NetworkResult.Success).data
        assertEquals("I am doing great, how can I help?", assistantMsg.content)
        assertEquals("assistant", assistantMsg.role)
        assertNull(assistantMsg.groundingMetadataJson)

        val capturedRequest = requestSlot.captured
        assertEquals("meta-llama-3-8b", capturedRequest.model)
        assertNull(capturedRequest.tools)
    }

    @Test
    fun `sendMessage with Gemini model and search grounding enabled sends correct request and saves groundingMetadata`() = runTest {
        val sessionId = "session-gemini"
        val existingSession = ChatSessionEntity(id = sessionId, title = "New Chat", modelName = "gemini-1.5-flash")
        val userMsg = ChatMessageEntity(id = "msg-1", sessionId = sessionId, role = "user", content = "What is the latest news?")

        coEvery { chatSessionDao.getSessionById(sessionId) } returns existingSession
        coEvery { chatMessageDao.getMessagesForSessionSync(sessionId) } returns listOf(userMsg)
        every { preferencesManager.getGeminiApiKeySync() } returns "AIzaSyFakeKey"

        val mockWebSource = GeminiWebSource(uri = "https://example.com/news", title = "Latest News Example")
        val mockChunk = GeminiGroundingChunk(web = mockWebSource)
        val mockMetadata = GeminiGroundingMetadata(
            webSearchQueries = listOf("What is the latest news"),
            groundingChunks = listOf(mockChunk),
            searchEntryPoint = GeminiSearchEntryPoint(renderedContent = "Search Entry Point HTML")
        )
        
        val mockCandidate = GeminiCandidate(
            content = GeminiContent(role = "model", parts = listOf(GeminiPart(text = "Here is the news from example.com"))),
            finishReason = "STOP",
            groundingMetadata = mockMetadata
        )
        val mockResponse = GeminiGenerateContentResponse(
            candidates = listOf(mockCandidate),
            usageMetadata = GeminiUsageMetadata(promptTokenCount = 10, candidatesTokenCount = 15, totalTokenCount = 25)
        )

        val requestSlot = slot<GeminiGenerateContentRequest>()
        coEvery { apiService.generateContent(
            model = "gemini-1.5-flash",
            apiKey = "AIzaSyFakeKey",
            request = capture(requestSlot)
        ) } returns Response.success(mockResponse)

        val result = repository.sendMessage(sessionId = sessionId, userPrompt = "What is the latest news?", isWebSearchActive = true)

        assertTrue(result is NetworkResult.Success)
        val assistantMsg = (result as NetworkResult.Success).data
        assertEquals("Here is the news from example.com", assistantMsg.content)
        assertEquals("assistant", assistantMsg.role)
        assertNotNull(assistantMsg.groundingMetadataJson)
        assertTrue(assistantMsg.groundingMetadataJson!!.contains("https://example.com/news"))

        val capturedRequest = requestSlot.captured
        assertNotNull(capturedRequest.tools)
        assertEquals(1, capturedRequest.tools!!.size)
        assertNotNull(capturedRequest.tools!![0].googleSearch)
    }

    @Test
    fun `sendMessage with non-Gemini model and search enabled maps model to gemini-2.5-flash`() = runTest {
        val sessionId = "session-local"
        val existingSession = ChatSessionEntity(id = sessionId, title = "New Chat", modelName = "meta-llama-3-8b")
        val userMsg = ChatMessageEntity(id = "msg-1", sessionId = sessionId, role = "user", content = "Tell me the weather")

        coEvery { chatSessionDao.getSessionById(sessionId) } returns existingSession
        coEvery { chatMessageDao.getMessagesForSessionSync(sessionId) } returns listOf(userMsg)
        every { preferencesManager.getGeminiApiKeySync() } returns "AIzaSyFakeKey"

        val mockCandidate = GeminiCandidate(
            content = GeminiContent(role = "model", parts = listOf(GeminiPart(text = "Weather is nice"))),
            finishReason = "STOP",
            groundingMetadata = null
        )
        val mockResponse = GeminiGenerateContentResponse(
            candidates = listOf(mockCandidate),
            usageMetadata = null
        )

        val modelSlot = slot<String>()
        coEvery { apiService.generateContent(
            model = capture(modelSlot),
            apiKey = any(),
            request = any()
        ) } returns Response.success(mockResponse)

        val result = repository.sendMessage(sessionId = sessionId, userPrompt = "Tell me the weather", isWebSearchActive = true)

        assertTrue(result is NetworkResult.Success)
        assertEquals("gemini-2.5-flash", modelSlot.captured)
    }

    @Test
    fun `sendMessage with specific Gemini model and search disabled routes to Gemini API directly`() = runTest {
        val sessionId = "session-specific-gemini"
        val existingSession = ChatSessionEntity(id = sessionId, title = "New Chat", modelName = "gemini-3.5-flash")
        val userMsg = ChatMessageEntity(id = "msg-1", sessionId = sessionId, role = "user", content = "Hello cloud")

        coEvery { chatSessionDao.getSessionById(sessionId) } returns existingSession
        coEvery { chatMessageDao.getMessagesForSessionSync(sessionId) } returns listOf(userMsg)
        every { preferencesManager.getGeminiApiKeySync() } returns "AIzaSyFakeKey"

        val mockCandidate = GeminiCandidate(
            content = GeminiContent(role = "model", parts = listOf(GeminiPart(text = "Hello user"))),
            finishReason = "STOP",
            groundingMetadata = null
        )
        val mockResponse = GeminiGenerateContentResponse(
            candidates = listOf(mockCandidate),
            usageMetadata = null
        )

        val modelSlot = slot<String>()
        val requestSlot = slot<GeminiGenerateContentRequest>()
        coEvery { apiService.generateContent(
            model = capture(modelSlot),
            apiKey = any(),
            request = capture(requestSlot)
        ) } returns Response.success(mockResponse)

        val result = repository.sendMessage(sessionId = sessionId, userPrompt = "Hello cloud", isWebSearchActive = false)

        assertTrue(result is NetworkResult.Success)
        assertEquals("gemini-3.5-flash", modelSlot.captured)
        assertNull(requestSlot.captured.tools) // Search is disabled, so tools should be null
    }

    @Test
    fun `sendMessage updates message status to ERROR on API exception`() = runTest {
        val sessionId = "session-123"
        val existingSession = ChatSessionEntity(id = sessionId, title = "New Chat", modelName = "gemini-1.5-flash")
        coEvery { chatSessionDao.getSessionById(sessionId) } returns existingSession
        coEvery { chatMessageDao.getMessagesForSessionSync(sessionId) } returns emptyList()
        every { preferencesManager.getGeminiApiKeySync() } returns "AIzaSyFakeKey"

        coEvery { apiService.generateContent(any(), any(), any()) } throws RuntimeException("API Error")

        val result = repository.sendMessage(sessionId = sessionId, userPrompt = "Test error", isWebSearchActive = true)

        assertTrue(result is NetworkResult.Error)
        coVerify { chatMessageDao.updateMessageStatus(any(), eq("ERROR")) }
    }

    @Test
    fun `loadModel calls apiService with correct request`() = runTest {
        val modelId = "test-model"
        coEvery { apiService.loadModelLmStudio(any()) } returns Response.success(Unit)

        val result = repository.loadModel(modelId)

        assertTrue(result is NetworkResult.Success)
        coVerify { apiService.loadModelLmStudio(ModelLoadRequest(modelId)) }
    }

    @Test
    fun `loadModel returns user-friendly memory error message on resource exhaustion JSON response`() = runTest {
        val modelId = "test-huge-model"
        val errorJson = "{\"error\": {\"message\": \"failed to allocate 8.5 GB of VRAM. Only 2.1 GB free.\", \"code\": \"out_of_memory\"}}"
        coEvery { apiService.loadModelLmStudio(any()) } returns Response.error(
            500,
            okhttp3.ResponseBody.create(null, errorJson)
        )

        val result = repository.loadModel(modelId)

        assertTrue(result is NetworkResult.Error)
        val errMsg = (result as NetworkResult.Error).message
        assertTrue(errMsg.contains("Not enough resources to load the model"))
        assertTrue(errMsg.contains("VRAM or RAM"))
    }

    @Test
    fun `loadModel returns user-friendly memory error message on plain text resource error`() = runTest {
        val modelId = "test-huge-model"
        coEvery { apiService.loadModelLmStudio(any()) } returns Response.error(
            500,
            okhttp3.ResponseBody.create(null, "OOM allocation failed on GPU")
        )

        val result = repository.loadModel(modelId)

        assertTrue(result is NetworkResult.Error)
        val errMsg = (result as NetworkResult.Error).message
        assertTrue(errMsg.contains("Not enough resources to load the model"))
    }

    @Test
    fun `unloadModel calls apiService with correct request`() = runTest {
        val instanceId = "test-instance"
        coEvery { apiService.unloadModelLmStudio(any()) } returns Response.success(Unit)

        val result = repository.unloadModel(instanceId)

        assertTrue(result is NetworkResult.Success)
        coVerify { apiService.unloadModelLmStudio(ModelUnloadRequest(instanceId)) }
    }

    @Test
    fun `sendMessage uses session-specific model to retrieve system prompt and model name`() = runTest {
        val sessionId = "session-456"
        val sessionModel = "custom-llm-model"
        val sessionPrompt = "You are a specialized math helper agent."
        
        val existingSession = ChatSessionEntity(id = sessionId, title = "Math Chat", modelName = sessionModel)
        coEvery { chatSessionDao.getSessionById(sessionId) } returns existingSession
        val userMsg = ChatMessageEntity(id = "msg-456", sessionId = sessionId, role = "user", content = "2+2")
        coEvery { chatMessageDao.getMessagesForSessionSync(sessionId) } returns listOf(userMsg)
        every { preferencesManager.getSystemPromptSync(sessionModel) } returns sessionPrompt

        val requestSlot = slot<ChatCompletionRequest>()
        val mockApiResponse = ChatCompletionResponse(
            id = "resp-2",
            created = 2000L,
            model = sessionModel,
            choices = listOf(
                Choice(index = 0, message = ResponseMessage(role = "assistant", content = "4"), finishReason = "stop")
            ),
            usage = Usage(5, 5, 10)
        )
        coEvery { apiService.createChatCompletion(capture(requestSlot)) } returns Response.success(mockApiResponse)

        repository.sendMessage(sessionId = sessionId, userPrompt = "2+2", isWebSearchActive = false)

        val capturedRequest = requestSlot.captured
        assertEquals(sessionModel, capturedRequest.model)
        
        val messages = capturedRequest.messages
        assertEquals(2, messages.size) // system prompt + user prompt
        assertEquals("system", messages[0].role)
        assertEquals(sessionPrompt, messages[0].content)
        
        verify { preferencesManager.getSystemPromptSync(sessionModel) }
    }

    @Test
    fun `fetchGeminiModels parses available models and filters correctly`() = runTest {
        every { preferencesManager.getGeminiApiKeySync() } returns "AIzaSyFakeKey"
        
        val mockModels = listOf(
            GeminiModelData(
                name = "models/gemini-2.5-flash",
                displayName = "Gemini 2.5 Flash",
                description = "Flash model",
                supportedGenerationMethods = listOf("generateContent", "countTokens")
            ),
            GeminiModelData(
                name = "models/gemini-embedding",
                displayName = "Gemini Embedding",
                description = "Embedding model",
                supportedGenerationMethods = listOf("embedContent")
            )
        )
        val mockResponse = GeminiModelListResponse(models = mockModels)
        coEvery { apiService.getGeminiModels("AIzaSyFakeKey") } returns Response.success(mockResponse)

        val result = repository.fetchGeminiModels()

        assertTrue(result is NetworkResult.Success)
        val list = (result as NetworkResult.Success).data
        assertEquals(1, list.size)
        assertEquals("gemini-2.5-flash", list[0])
    }
}
