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
    fun `sendMessage fetches history and sends correct prompt array to Gemini SDK`() = runTest {
        val sessionId = "session-123"
        val existingSession = ChatSessionEntity(id = sessionId, title = "New Chat", modelName = "gemini-1.5-flash")
        val oldUserMsg = ChatMessageEntity(id = "msg-1", sessionId = sessionId, role = "user", content = "Hello")
        val oldAssistantMsg = ChatMessageEntity(id = "msg-2", sessionId = sessionId, role = "assistant", content = "Hi there!")

        coEvery { chatSessionDao.getSessionById(sessionId) } returns existingSession
        val newUserMsg = ChatMessageEntity(id = "msg-3", sessionId = sessionId, role = "user", content = "How are you?")
        coEvery { chatMessageDao.getMessagesForSessionSync(sessionId) } returns listOf(oldUserMsg, oldAssistantMsg, newUserMsg)
        every { preferencesManager.getGeminiApiKeySync() } returns "AIzaSyFakeKey"

        val mockClient = mockk<com.google.genai.kotlin.Client>()
        val mockModels = mockk<com.google.genai.kotlin.Models>()
        every { repository.createGenAiClient(any()) } returns mockClient
        every { mockClient.models } returns mockModels

        val mockCandidate = com.google.genai.kotlin.types.Candidate(
            content = com.google.genai.kotlin.types.Content(role = "model", parts = listOf(com.google.genai.kotlin.types.Part(text = "I am doing great"))),
            finishReason = com.google.genai.kotlin.types.FinishReason.STOP
        )
        val mockResponse = com.google.genai.kotlin.types.GenerateContentResponse(
            candidates = listOf(mockCandidate)
        )

        val contentsSlot = slot<List<com.google.genai.kotlin.types.Content>>()
        coEvery { mockModels.generateContent(
            model = "gemini-1.5-flash",
            contents = capture(contentsSlot),
            config = any()
        ) } returns mockResponse

        val result = repository.sendMessage(sessionId = sessionId, userPrompt = "How are you?")

        assertTrue(result is NetworkResult.Success)
        val assistantMsg = (result as NetworkResult.Success).data
        assertEquals("I am doing great", assistantMsg.content)
        assertEquals("assistant", assistantMsg.role)

        val capturedContents = contentsSlot.captured
        assertEquals(3, capturedContents.size)
    }

    @Test
    fun `sendMessage with Gemini model and search grounding enabled sends correct request and saves groundingMetadata`() = runTest {
        val sessionId = "session-gemini"
        val existingSession = ChatSessionEntity(id = sessionId, title = "New Chat", modelName = "gemini-1.5-flash")
        val userMsg = ChatMessageEntity(id = "msg-1", sessionId = sessionId, role = "user", content = "What is the latest news?")

        coEvery { chatSessionDao.getSessionById(sessionId) } returns existingSession
        coEvery { chatMessageDao.getMessagesForSessionSync(sessionId) } returns listOf(userMsg)
        every { preferencesManager.getGeminiApiKeySync() } returns "AIzaSyFakeKey"

        val mockClient = mockk<com.google.genai.kotlin.Client>()
        val mockModels = mockk<com.google.genai.kotlin.Models>()
        every { repository.createGenAiClient(any()) } returns mockClient
        every { mockClient.models } returns mockModels
        
        val mockWebSource = com.google.genai.kotlin.types.GroundingChunkWeb(uri = "https://example.com/news", title = "Latest News Example")
        val mockChunk = com.google.genai.kotlin.types.GroundingChunk(web = mockWebSource)
        val mockMetadata = com.google.genai.kotlin.types.GroundingMetadata(
            webSearchQueries = listOf("What is the latest news"),
            groundingChunks = listOf(mockChunk),
            searchEntryPoint = com.google.genai.kotlin.types.SearchEntryPoint(renderedContent = "Search Entry Point HTML")
        )
        
        val mockCandidate = com.google.genai.kotlin.types.Candidate(
            content = com.google.genai.kotlin.types.Content(role = "model", parts = listOf(com.google.genai.kotlin.types.Part(text = "Here is the news from example.com"))),
            finishReason = com.google.genai.kotlin.types.FinishReason.STOP,
            groundingMetadata = mockMetadata
        )
        
        val mockResponse = com.google.genai.kotlin.types.GenerateContentResponse(
            candidates = listOf(mockCandidate),
            usageMetadata = com.google.genai.kotlin.types.GenerateContentResponseUsageMetadata(promptTokenCount = 10, candidatesTokenCount = 15, totalTokenCount = 25)
        )

        val configSlot = slot<com.google.genai.kotlin.types.GenerateContentConfig>()
        
        coEvery { mockModels.generateContent(
            model = "gemini-1.5-flash",
            contents = any(),
            config = capture(configSlot)
        ) } returns mockResponse

        val result = repository.sendMessage(sessionId = sessionId, userPrompt = "What is the latest news?", isWebSearchActive = true)
        if (result is NetworkResult.Error) {
            println("GEMINI_TEST_ERROR: ${result.message} / Exception: ${result.exception}")
        }
        assertTrue(result is NetworkResult.Success)
        val assistantMsg = (result as NetworkResult.Success).data
        assertEquals("Here is the news from example.com", assistantMsg.content)
        assertEquals("assistant", assistantMsg.role)
        assertNotNull(assistantMsg.groundingMetadataJson)
        assertTrue(assistantMsg.groundingMetadataJson!!.contains("https://example.com/news"))

        val capturedConfig = configSlot.captured
        assertNotNull(capturedConfig.tools)
        assertEquals(1, capturedConfig.tools!!.size)
        assertNotNull(capturedConfig.tools!![0].googleSearch)
    }

    @Test
    fun `sendMessage updates message status to ERROR on SDK exception`() = runTest {
        val sessionId = "session-123"
        val existingSession = ChatSessionEntity(id = sessionId, title = "New Chat", modelName = "gemini-1.5-flash")
        coEvery { chatSessionDao.getSessionById(sessionId) } returns existingSession
        coEvery { chatMessageDao.getMessagesForSessionSync(sessionId) } returns emptyList()
        every { preferencesManager.getGeminiApiKeySync() } returns "AIzaSyFakeKey"

        val mockClient = mockk<com.google.genai.kotlin.Client>()
        val mockModels = mockk<com.google.genai.kotlin.Models>()
        every { repository.createGenAiClient(any()) } returns mockClient
        every { mockClient.models } returns mockModels

        coEvery { mockModels.generateContent(
            model = any<String>(),
            contents = any<List<com.google.genai.kotlin.types.Content>>(),
            config = any<com.google.genai.kotlin.types.GenerateContentConfig>()
        ) } throws RuntimeException("SDK Error")

        val result = repository.sendMessage(sessionId = sessionId, userPrompt = "Test error")

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
        every { preferencesManager.getGeminiApiKeySync() } returns "AIzaSyFakeKey"

        val mockClient = mockk<com.google.genai.kotlin.Client>()
        val mockModels = mockk<com.google.genai.kotlin.Models>()
        every { repository.createGenAiClient(any()) } returns mockClient
        every { mockClient.models } returns mockModels

        val mockCandidate = com.google.genai.kotlin.types.Candidate(
            content = com.google.genai.kotlin.types.Content(role = "model", parts = listOf(com.google.genai.kotlin.types.Part(text = "4"))),
            finishReason = com.google.genai.kotlin.types.FinishReason.STOP
        )
        val mockResponse = com.google.genai.kotlin.types.GenerateContentResponse(
            candidates = listOf(mockCandidate)
        )

        val modelSlot = slot<String>()
        coEvery { mockModels.generateContent(
            model = capture(modelSlot),
            contents = any(),
            config = any()
        ) } returns mockResponse

        repository.sendMessage(sessionId = sessionId, userPrompt = "2+2")

        assertEquals(sessionModel, modelSlot.captured)
        verify { preferencesManager.getSystemPromptSync(sessionModel) }
    }
}
