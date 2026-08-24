package com.example.lmstudioclient.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.lmstudioclient.LMStudioApplication
import com.example.lmstudioclient.ui.chat.ChatScreen
import com.example.lmstudioclient.ui.chat.ChatViewModel
import com.example.lmstudioclient.ui.settings.SettingsScreen
import com.example.lmstudioclient.ui.settings.SettingsViewModel
import com.example.lmstudioclient.ui.theme.LMStudioTheme

class MainActivity : ComponentActivity() {

    private val chatViewModel: ChatViewModel by viewModels {
        val app = application as LMStudioApplication
        ChatViewModel.Factory(app, app.repository, app.preferencesManager)
    }

    private val settingsViewModel: SettingsViewModel by viewModels {
        val app = application as LMStudioApplication
        SettingsViewModel.Factory(app.preferencesManager, app.repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val preferencesManager = (application as LMStudioApplication).preferencesManager

        setContent {
            val isDarkModeOverride by preferencesManager.isDarkModeFlow.collectAsState(initial = preferencesManager.getDarkModeSync())

            LMStudioTheme(isDarkModeOverride = isDarkModeOverride) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentScreen by remember { mutableStateOf(Screen.Chat) }

                    when (currentScreen) {
                        Screen.Chat -> {
                            ChatScreen(
                                viewModel = chatViewModel,
                                onOpenSettings = { currentScreen = Screen.Settings }
                            )
                        }
                        Screen.Settings -> {
                            SettingsScreen(
                                viewModel = settingsViewModel,
                                onBack = { currentScreen = Screen.Chat }
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class Screen {
    Chat,
    Settings
}
