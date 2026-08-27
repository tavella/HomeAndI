package es.zombielawng.nom.homeandi.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import es.zombielawng.nom.homeandi.ui.components.ConnectionState
import es.zombielawng.nom.homeandi.ui.components.ConnectionStatusBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var expanded by remember { mutableStateOf(false) }
    var showApiKey by remember { mutableStateOf(false) }
    var modelToDelete by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.saveMessage) {
        state.saveMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSaveMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Server & Model Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
                .imePadding()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Appearance Section
            Text(text = "Appearance", style = MaterialTheme.typography.titleMedium)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Theme Mode", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = when(state.themeMode) {
                                    "light" -> "Always Light"
                                    "dark" -> "Always Dark"
                                    "warm_navy" -> "Warm Navy"
                                    else -> "Follow System"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row {
                            IconButton(onClick = { viewModel.updateThemeMode("light") }) {
                                Icon(
                                    imageVector = Icons.Rounded.LightMode,
                                    contentDescription = "Light Mode",
                                    tint = if (state.themeMode == "light") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                            }
                            IconButton(onClick = { viewModel.updateThemeMode("dark") }) {
                                Icon(
                                    imageVector = Icons.Rounded.DarkMode,
                                    contentDescription = "Dark Mode",
                                    tint = if (state.themeMode == "dark") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                            }
                            IconButton(onClick = { viewModel.updateThemeMode("warm_navy") }) {
                                Icon(
                                    imageVector = Icons.Rounded.Palette,
                                    contentDescription = "Warm Navy Mode",
                                    tint = if (state.themeMode == "warm_navy") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                            }
                            IconButton(onClick = { viewModel.updateThemeMode("system") }) {
                                Icon(
                                    imageVector = Icons.Rounded.Contrast,
                                    contentDescription = "System Mode",
                                    tint = if (state.themeMode == "system") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(text = "Show Conversation Previews", style = MaterialTheme.typography.bodyLarge)
                            Text(text = "Display screenshots in session slideout", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                        }
                        Switch(
                            checked = state.isScreenshotsEnabled,
                            onCheckedChange = { viewModel.updateScreenshotsEnabled(it) }
                        )
                    }
                }
            }

            // Connection Status Section
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Connection Status",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val connectionState = state.connectionState
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ConnectionStatusBadge(state = connectionState)

                        Button(
                            onClick = { viewModel.testConnection() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(imageVector = Icons.Rounded.NetworkCheck, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test Ping")
                        }
                    }

                    if (connectionState is ConnectionState.Error) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = connectionState.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            // Host & Port Settings
            Text(text = "HomeAndI Server Connection", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = state.host,
                onValueChange = { viewModel.updateHost(it) },
                label = { Text("Hostname / IP Address") },
                placeholder = { Text("10.0.2.2 or 192.168.1.100") },
                leadingIcon = { Icon(Icons.Rounded.Dns, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = state.port,
                onValueChange = { viewModel.updatePort(it) },
                label = { Text("Port Number") },
                placeholder = { Text("8000") },
                leadingIcon = { Icon(Icons.Rounded.Numbers, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = state.apiKey,
                onValueChange = { viewModel.updateApiKey(it) },
                label = { Text("API Key") },
                placeholder = { Text("Optional - for servers that require a key") },
                leadingIcon = { Icon(Icons.Rounded.Key, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { showApiKey = !showApiKey }) {
                        Icon(
                            imageVector = if (showApiKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = if (showApiKey) "Hide API key" else "Show API key"
                        )
                    }
                },
                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Button(
                onClick = { viewModel.saveSettings() },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(imageVector = Icons.Rounded.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Server Configuration")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Model Management", style = MaterialTheme.typography.titleMedium)
                if (state.connectionState is ConnectionState.Testing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = { viewModel.fetchModels() }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Fetch Models", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            // Server Model Management
            if (state.availableModels.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Model ID", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(2f))
                            Text("Status", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                            Text("Action", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1.2f))
                            Text("Delete", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(0.8f))
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        state.availableModels.forEach { model ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = model.displayName ?: model.id,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(2f),
                                    maxLines = 1
                                )
                                
                                val isLoaded = model.isLoaded
                                Text(
                                    text = if (isLoaded) "LOADED" else "Idle",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isLoaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.weight(1f)
                                )

                                Box(modifier = Modifier.weight(1.2f)) {
                                    if (isLoaded) {
                                        val instanceId = model.loadedInstances?.firstOrNull()?.id ?: model.id
                                        TextButton(
                                            onClick = { viewModel.unloadModel(instanceId) },
                                            contentPadding = PaddingValues(0.dp),
                                            enabled = !state.isModelActionLoading
                                        ) {
                                            Text("Unload", color = MaterialTheme.colorScheme.error)
                                        }
                                    } else {
                                        TextButton(
                                            onClick = { viewModel.loadModel(model.id) },
                                            contentPadding = PaddingValues(0.dp),
                                            enabled = !state.isModelActionLoading
                                        ) {
                                            Text("Load")
                                        }
                                    }
                                }

                                IconButton(
                                    onClick = { modelToDelete = model.id },
                                    modifier = Modifier.weight(0.8f),
                                    enabled = !state.isModelActionLoading
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Delete,
                                        contentDescription = "Delete Model",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }

            val connectionState = state.connectionState
            if (state.availableModels.isNotEmpty()) {
                Text(
                    text = "✓ Discovered ${state.availableModels.size} models from server",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            } else if (connectionState is ConnectionState.Error) {
                Text(
                    text = "⚠️ Error: ${connectionState.message}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // System Prompt Configuration
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "System Prompt Context", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { viewModel.resetSystemPrompt() }) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset to Recommended", style = MaterialTheme.typography.labelLarge)
                }
            }

            OutlinedTextField(
                value = state.systemPrompt,
                onValueChange = { viewModel.updateSystemPrompt(it) },
                label = { Text("System Instructions") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                maxLines = 4
            )
        }
    }

    // Model Action Progress Modal
    if (state.isModelActionLoading) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { }, // Prevent dismissal during critical model actions
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = state.modelActionMessage ?: "Processing...",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
    // Second chance model deletion confirmation modal
    if (modelToDelete != null) {
        AlertDialog(
            onDismissRequest = { modelToDelete = null },
            title = { Text("Delete Model") },
            text = { Text("Are you sure you want to delete this model from the local server? This action is permanent and cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        modelToDelete?.let { viewModel.deleteModel(it) }
                        modelToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { modelToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
