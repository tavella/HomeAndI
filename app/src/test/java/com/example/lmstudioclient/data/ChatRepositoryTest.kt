package com.example.lmstudioclient.data

import com.example.lmstudioclient.data.local.ChatMessageDao
import com.example.lmstudioclient.data.local.ChatMessageEntity
import com.example.lmstudioclient.data.local.ChatSessionDao
import com.example.lmstudioclient.data.local.ChatSessionEntity
import com.example.lmstudioclient.data.preferences.ServerPreferencesManager
import com.example.lmstudioclient.data.remote.*
import com.example.lmstudioclient.data.repository.ChatRepository
import com.example.lmstudioclient.util.NetworkResult
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
    private val apiService: LMStudioApiService = mockk()
    private val preferencesManager: ServerPreferencesManager = mockk()
    private val context: android.content.Context = mockk(relaxed = true)

    private lateinit var repository: ChatRepository

    @Before
    fun setUp() {
        every { preferencesManager.getModelSync() } returns "meta-llama-3-8b"
        every { preferencesManager.getSystemPromptSync() } returns "You are a helpful assistant."

        repository = ChatRepository(
            chatSessionDao = chatSessionDao,
            chatMessageDao = chatMessageDao,
            apiService = apiService,
            preferencesManager = preferencesManager,
            context = context
        )
    }

    @Test
    fun `sendMessage fetches complete contiguous history and sends correct prompt array to API`() = runTest {
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

        val result = repository.sendMessage(sessionId = sessionId, userPrompt = "How are you?")

        // 1. Validate result success
        assertTrue(result is NetworkResult.Success)
        val assistantMsg = (result as NetworkResult.Success).data
        assertEquals("I am doing great, how can I help?", assistantMsg.content)
        assertEquals("assistant", assistantMsg.role)

        // 2. Validate exact payload sent to Retrofit
        val capturedRequest = requestSlot.captured
        assertEquals("meta-llama-3-8b", capturedRequest.model)

        // Messages array should have: System message + Old User + Old Assistant + New User prompt
        val messages = capturedRequest.messages
        assertEquals(4, messages.size)

        assertEquals("system", messages[0].role)
        assertEquals("You are a helpful assistant.", messages[0].content)

        assertEquals("user", messages[1].role)
        assertEquals("Hello", messages[1].content)

        assertEquals("assistant", messages[2].role)
        assertEquals("Hi there!", messages[2].content)

        assertEquals("user", messages[3].role)

        // 3. Verify Room Database interactions
        coVerify { chatMessageDao.insertMessage(any()) }
        coVerify { chatSessionDao.updateSession(any()) }
    }

    @Test
    fun `sendMessage updates message status to ERROR on API HTTP error`() = runTest {
        val sessionId = "session-123"
        coEvery { chatSessionDao.getSessionById(sessionId) } returns null
        coEvery { chatMessageDao.getMessagesForSessionSync(sessionId) } returns emptyList()

        coEvery { apiService.createChatCompletion(any()) } returns Response.error(
            500,
            okhttp3.ResponseBody.create(null, "Internal Server Error")
        )

        val result = repository.sendMessage(sessionId = sessionId, userPrompt = "Test error")

        assertTrue(result is NetworkResult.Error)
        val errorResult = result as NetworkResult.Error
        assertTrue(errorResult.message.contains("500"))

        // Verify status update in Room
        coVerify { chatMessageDao.updateMessageStatus(any(), eq("ERROR")) }
    }

    @Test
    fun `loadModel calls apiService with correct request`() = runTest {
        val modelId = "test-model"
        coEvery { apiService.loadModel(any()) } returns Response.success(Unit)

        val result = repository.loadModel(modelId)

        assertTrue(result is NetworkResult.Success)
        coVerify { apiService.loadModel(ModelLoadRequest(modelId)) }
    }

    @Test
    fun `unloadModel calls apiService with correct request`() = runTest {
        val instanceId = "test-instance"
        coEvery { apiService.unloadModel(any()) } returns Response.success(Unit)

        val result = repository.unloadModel(instanceId)

        assertTrue(result is NetworkResult.Success)
        coVerify { apiService.unloadModel(ModelUnloadRequest(instanceId)) }
    }
}
