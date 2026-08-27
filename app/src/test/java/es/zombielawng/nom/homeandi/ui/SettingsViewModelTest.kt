package es.zombielawng.nom.homeandi.ui

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import es.zombielawng.nom.homeandi.data.preferences.ServerPreferencesManager
import es.zombielawng.nom.homeandi.data.remote.ModelData
import es.zombielawng.nom.homeandi.data.repository.ChatRepository
import es.zombielawng.nom.homeandi.ui.components.ConnectionState
import es.zombielawng.nom.homeandi.ui.settings.SettingsViewModel
import es.zombielawng.nom.homeandi.util.NetworkResult
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private val preferencesManager: ServerPreferencesManager = mockk(relaxed = true)
    private val repository: ChatRepository = mockk(relaxed = true)
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { preferencesManager.getHostSync() } returns "192.168.1.100"
        every { preferencesManager.getPortSync() } returns 1234
        every { preferencesManager.getApiKeySync() } returns ""
        every { preferencesManager.getModelSync() } returns "local-model"
        every { preferencesManager.getSystemPromptSync(any()) } returns "Test System Prompt"

        viewModel = SettingsViewModel(preferencesManager, repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state loads saved server preferences`() {
        val state = viewModel.uiState.value
        assertEquals("192.168.1.100", state.host)
        assertEquals("1234", state.port)
        assertEquals("", state.apiKey)
        assertEquals("local-model", state.modelName)
        assertEquals("Test System Prompt", state.systemPrompt)
    }

    @Test
    fun `testConnection updates connectionState and selects first loaded model`() = runTest {
        val loadedModel = ModelData("llama-3-8b", loadedInstances = listOf(mockk()))
        val idleModel = ModelData("qwen-7b")
        val mockModels = listOf(idleModel, loadedModel)
        
        coEvery { repository.testConnection() } returns NetworkResult.Success(mockModels)

        viewModel.testConnection()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.connectionState is ConnectionState.Connected)
        assertEquals(2, (state.connectionState as ConnectionState.Connected).modelCount)
        assertEquals(mockModels, state.availableModels)
        assertEquals("llama-3-8b", state.modelName) // Should pick the loaded one

        verify { preferencesManager.updateServerConfig("192.168.1.100", 1234, "local-model", "Test System Prompt", "") }
    }

    @Test
    fun `testConnection updates connectionState to Error on failure`() = runTest {
        coEvery { repository.testConnection() } returns NetworkResult.Error("Host Unreachable")

        viewModel.testConnection()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.connectionState is ConnectionState.Error)
        assertEquals("Host Unreachable", (state.connectionState as ConnectionState.Error).message)
    }

    @Test
    fun `saveSettings calls preferencesManager updateServerConfig`() {
        viewModel.updateHost("10.0.0.5")
        viewModel.updatePort("8080")
        viewModel.updateModelName("gemma-2-9b")

        viewModel.saveSettings()

        verify { preferencesManager.updateServerConfig("10.0.0.5", 8080, "gemma-2-9b", "Test System Prompt", "") }
        assertNotNull(viewModel.uiState.value.saveMessage)
    }

    @Test
    fun `loadModel calls repository and updates state on success`() = runTest {
        val modelId = "test-model"
        coEvery { repository.loadModel(modelId) } returns NetworkResult.Success(Unit)
        coEvery { repository.testConnection() } returns NetworkResult.Success(emptyList())

        viewModel.loadModel(modelId)
        
        assertTrue(viewModel.uiState.value.isModelActionLoading)
        assertEquals("Now loading test-model...", viewModel.uiState.value.modelActionMessage)
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertFalse(viewModel.uiState.value.isModelActionLoading)
        assertNull(viewModel.uiState.value.modelActionMessage)
        assertTrue(viewModel.uiState.value.saveMessage?.contains("successfully") == true)
        coVerify { repository.loadModel(modelId) }
        coVerify { repository.testConnection() } // Verify it refreshes the list
    }

    @Test
    fun `unloadModel calls repository and updates state on success`() = runTest {
        val modelId = "test-model"
        coEvery { repository.unloadModel(modelId) } returns NetworkResult.Success(Unit)
        coEvery { repository.testConnection() } returns NetworkResult.Success(emptyList())

        viewModel.unloadModel(modelId)
        
        assertTrue(viewModel.uiState.value.isModelActionLoading)
        assertEquals("Unloading test-model...", viewModel.uiState.value.modelActionMessage)
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertFalse(viewModel.uiState.value.isModelActionLoading)
        assertNull(viewModel.uiState.value.modelActionMessage)
        assertTrue(viewModel.uiState.value.saveMessage?.contains("successfully") == true)
        coVerify { repository.unloadModel(modelId) }
        coVerify { repository.testConnection() } // Verify it refreshes the list
    }

    @Test
    fun `updateModelName updates systemPrompt in UI state to model-specific prompt`() {
        val specificPrompt = "You are a code completion model."
        every { preferencesManager.getSystemPromptSync("code-llama") } returns specificPrompt

        viewModel.updateModelName("code-llama")

        val state = viewModel.uiState.value
        assertEquals("code-llama", state.modelName)
        assertEquals(specificPrompt, state.systemPrompt)
    }
}
