package com.example.lmstudioclient.ui

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.lmstudioclient.data.preferences.ServerPreferencesManager
import com.example.lmstudioclient.data.remote.ModelData
import com.example.lmstudioclient.data.repository.ChatRepository
import com.example.lmstudioclient.ui.components.ConnectionState
import com.example.lmstudioclient.ui.settings.SettingsViewModel
import com.example.lmstudioclient.util.NetworkResult
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
        every { preferencesManager.getModelSync() } returns "local-model"
        every { preferencesManager.getSystemPromptSync() } returns "Test System Prompt"

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
        assertEquals("local-model", state.modelName)
        assertEquals("Test System Prompt", state.systemPrompt)
    }

    @Test
    fun `testConnection updates connectionState to Connected on success`() = runTest {
        val mockModels = listOf(ModelData("llama-3-8b"), ModelData("qwen-7b"))
        coEvery { repository.testConnection() } returns NetworkResult.Success(mockModels)

        viewModel.testConnection()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.connectionState is ConnectionState.Connected)
        assertEquals(2, (state.connectionState as ConnectionState.Connected).modelCount)
        assertEquals(listOf("llama-3-8b", "qwen-7b"), state.availableModels)

        verify { preferencesManager.updateServerConfig("192.168.1.100", 1234, "local-model", "Test System Prompt") }
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

        verify { preferencesManager.updateServerConfig("10.0.0.5", 8080, "gemma-2-9b", "Test System Prompt") }
        assertNotNull(viewModel.uiState.value.saveMessage)
    }
}
