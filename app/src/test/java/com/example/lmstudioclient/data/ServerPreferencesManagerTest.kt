package com.example.lmstudioclient.data

import android.content.Context
import android.content.SharedPreferences
import com.example.lmstudioclient.data.preferences.ServerPreferencesManager
import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ServerPreferencesManagerTest {

    private val context: Context = mockk()
    private val sharedPreferences: SharedPreferences = mockk(relaxed = true)
    private val editor: SharedPreferences.Editor = mockk(relaxed = true)

    private lateinit var preferencesManager: ServerPreferencesManager

    @Before
    fun setUp() {
        every { context.getSharedPreferences("lm_studio_prefs", Context.MODE_PRIVATE) } returns sharedPreferences
        every { sharedPreferences.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.putInt(any(), any()) } returns editor

        preferencesManager = ServerPreferencesManager(context)
    }

    @Test
    fun `getSystemPromptSync returns default prompt when no custom prompt is stored`() {
        every { sharedPreferences.getString("system_prompt_my-model", any()) } returns null
        val prompt = preferencesManager.getSystemPromptSync("my-model")
        assertEquals(ServerPreferencesManager.DEFAULT_SYSTEM_PROMPT, prompt)
    }

    @Test
    fun `getSystemPromptSync returns specific prompt when stored for model`() {
        val customPrompt = "You are a specialized math AI helper."
        every { sharedPreferences.getString("system_prompt_math-model", any()) } returns customPrompt
        val prompt = preferencesManager.getSystemPromptSync("math-model")
        assertEquals(customPrompt, prompt)
    }

    @Test
    fun `getApiKeySync returns empty string when no key is stored`() {
        every { sharedPreferences.getString("server_api_key", any()) } returns null
        assertEquals("", preferencesManager.getApiKeySync())
    }

    @Test
    fun `getApiKeySync returns stored key`() {
        every { sharedPreferences.getString("server_api_key", any()) } returns "sk-abc123"
        assertEquals("sk-abc123", preferencesManager.getApiKeySync())
    }

    @Test
    fun `updateServerConfig trims and saves api key`() {
        preferencesManager.updateServerConfig("127.0.0.1", 5001, "code-llama", "prompt", "  sk-abc123  ")

        verify {
            editor.putString("server_api_key", "sk-abc123")
        }
    }

    @Test
    fun `updateServerConfig saves system prompt keyed by model name`() {
        val host = "127.0.0.1"
        val port = 5001
        val model = "code-llama"
        val customPrompt = "You are a code completion agent."

        preferencesManager.updateServerConfig(host, port, model, customPrompt)

        verify {
            editor.putString("server_host", "127.0.0.1")
            editor.putInt("server_port", 5001)
            editor.putString("server_model", "code-llama")
            editor.putString("system_prompt_code-llama", customPrompt)
            editor.putString("system_prompt", customPrompt)
            editor.apply()
        }
    }
}
