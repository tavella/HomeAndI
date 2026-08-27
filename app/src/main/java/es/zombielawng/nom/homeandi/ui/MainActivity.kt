package es.zombielawng.nom.homeandi.ui

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
import es.zombielawng.nom.homeandi.HomeAndIApplication
import es.zombielawng.nom.homeandi.ui.chat.ChatScreen
import es.zombielawng.nom.homeandi.ui.chat.ChatViewModel
import es.zombielawng.nom.homeandi.ui.settings.SettingsScreen
import es.zombielawng.nom.homeandi.ui.settings.SettingsViewModel
import es.zombielawng.nom.homeandi.ui.theme.HomeAndITheme

class MainActivity : ComponentActivity() {

    private val chatViewModel: ChatViewModel by viewModels {
        val app = application as HomeAndIApplication
        ChatViewModel.Factory(app, app.repository, app.preferencesManager)
    }

    private val settingsViewModel: SettingsViewModel by viewModels {
        val app = application as HomeAndIApplication
        SettingsViewModel.Factory(app.preferencesManager, app.repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val preferencesManager = (application as HomeAndIApplication).preferencesManager

        setContent {
            val isDarkModeOverride by preferencesManager.isDarkModeFlow.collectAsState(initial = preferencesManager.getDarkModeSync())

            HomeAndITheme(isDarkModeOverride = isDarkModeOverride) {
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
