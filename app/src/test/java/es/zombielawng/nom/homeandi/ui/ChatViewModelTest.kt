package es.zombielawng.nom.homeandi.ui

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import es.zombielawng.nom.homeandi.data.local.ChatMessageEntity
import es.zombielawng.nom.homeandi.data.local.ChatSessionEntity
import es.zombielawng.nom.homeandi.data.preferences.ServerPreferencesManager
import es.zombielawng.nom.homeandi.data.repository.ChatRepository
import es.zombielawng.nom.homeandi.ui.chat.ChatViewModel
import es.zombielawng.nom.homeandi.util.NetworkResult
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private val application: Application = mockk(relaxed = true)
    private val repository: ChatRepository = mockk(relaxed = true)
    private val preferencesManager: ServerPreferencesManager = mockk(relaxed = true)
    private val workManager: WorkManager = mockk(relaxed = true)
    private val sessionsFlow = MutableStateFlow<List<ChatSessionEntity>>(emptyList())
    private lateinit var viewModel: ChatViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { application.applicationContext } returns application
        every { workManager.getWorkInfosForUniqueWorkFlow(any()) } returns flowOf(emptyList())

        val defaultSession = ChatSessionEntity("session-1", "Default Session", "meta-llama-3-8b")
        sessionsFlow.value = listOf(defaultSession)
        every { repository.getAllSessions() } returns sessionsFlow
        every { repository.getMessagesForSession(any()) } returns flowOf(emptyList())
        every { preferencesManager.getLastSessionIdSync() } returns "session-1"

        viewModel = ChatViewModel(application, repository, preferencesManager, workManager)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state loads sessions and selects active session from preferences`() = runTest {
        val state = viewModel.uiState.value
        assertEquals(1, state.sessions.size)
        assertEquals("session-1", state.activeSession?.id)
        assertFalse(state.isGenerating)
        assertNull(state.errorMessage)
    }

    @Test
    fun `sendMessage enqueues worker and updates state`() = runTest {
        viewModel.sendMessage("Hello world")

        // In the new architecture, sendMessage calls ChatWorker.enqueue
        // which internally uses WorkManager.
        verify { workManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>()) }
        
        // We also check if isGenerating is set to true immediately
        assertTrue(viewModel.uiState.value.isGenerating)
        assertTrue(viewModel.uiState.value.pendingAttachments.isEmpty()) // Should be cleared after send
    }

    @Test
    fun `observeWorkStatus updates isGenerating and errorMessage based on WorkInfo`() = runTest {
        val workInfoFlow = MutableStateFlow<List<WorkInfo>>(emptyList())
        every { workManager.getWorkInfosForUniqueWorkFlow("send_message_session-1") } returns workInfoFlow
        
        // Trigger re-subscription in VM if needed or just re-init
        viewModel.selectSession("session-1")
        testDispatcher.scheduler.advanceUntilIdle()

        // Simulate Running state
        val runningWorkInfo = mockk<WorkInfo>()
        every { runningWorkInfo.state } returns WorkInfo.State.RUNNING
        workInfoFlow.value = listOf(runningWorkInfo)
        
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isGenerating)

        // Simulate Success state
        val successWorkInfo = mockk<WorkInfo>()
        every { successWorkInfo.state } returns WorkInfo.State.SUCCEEDED
        workInfoFlow.value = listOf(successWorkInfo)
        
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isGenerating)
    }

    @Test
    fun `addAttachment and removeAttachment update pendingAttachments list`() {
        viewModel.addAttachment("/path/to/image.png")
        assertEquals(listOf("/path/to/image.png"), viewModel.uiState.value.pendingAttachments)

        viewModel.removeAttachment("/path/to/image.png")
        assertTrue(viewModel.uiState.value.pendingAttachments.isEmpty())
    }

    @Test
    fun `openAttachment and closeAttachment update viewingAttachment state`() {
        viewModel.openAttachment("/path/to/view.jpg")
        assertEquals("/path/to/view.jpg", viewModel.uiState.value.viewingAttachment)

        viewModel.closeAttachment()
        assertNull(viewModel.uiState.value.viewingAttachment)
    }

    @Test
    fun `selectSession updates preferences and repository observation`() {
        val newSession = ChatSessionEntity("session-2", "New Session", "model")
        sessionsFlow.value = listOf(newSession)
        testDispatcher.scheduler.advanceUntilIdle()
        
        viewModel.selectSession("session-2")
        
        verify { preferencesManager.updateLastSessionId("session-2") }
        verify { repository.getMessagesForSession("session-2") }
    }
}
