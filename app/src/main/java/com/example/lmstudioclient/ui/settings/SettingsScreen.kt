package com.example.lmstudioclient.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.lmstudioclient.ui.components.ConnectionState
import com.example.lmstudioclient.ui.components.ConnectionStatusBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var expanded by remember { mutableStateOf(false) }

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
                        Column {
                            Text(text = "Theme Mode", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = when(state.isDarkMode) {
                                    null -> "Follow System"
                                    false -> "Always Light"
                                    true -> "Always Dark"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row {
                            IconButton(onClick = { viewModel.updateDarkMode(false) }) {
                                Icon(
                                    imageVector = Icons.Rounded.LightMode,
                                    contentDescription = "Light Mode",
                                    tint = if (state.isDarkMode == false) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                            }
                            IconButton(onClick = { viewModel.updateDarkMode(true) }) {
                                Icon(
                                    imageVector = Icons.Rounded.DarkMode,
                                    contentDescription = "Dark Mode",
                                    tint = if (state.isDarkMode == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                            }
                            IconButton(onClick = { viewModel.updateDarkMode(null) }) {
                                Icon(
                                    imageVector = Icons.Rounded.Contrast,
                                    contentDescription = "System Mode",
                                    tint = if (state.isDarkMode == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                            }
                        }
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
                placeholder = { Text("1234") },
                leadingIcon = { Icon(Icons.Rounded.Numbers, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Model Configuration
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Target LLM Model", style = MaterialTheme.typography.titleMedium)
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

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { 
                    if (state.availableModels.isNotEmpty()) {
                        expanded = !expanded 
                    } else {
                        viewModel.fetchModels()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = state.modelName,
                    onValueChange = { viewModel.updateModelName(it) },
                    readOnly = state.availableModels.isNotEmpty(),
                    label = { Text("Model Identifier") },
                    placeholder = { Text("Select or type a model...") },
                    leadingIcon = { Icon(Icons.Rounded.Psychology, contentDescription = null) },
                    trailingIcon = {
                        if (state.availableModels.isNotEmpty()) {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "Query models",
                                modifier = Modifier.clickable { viewModel.fetchModels() }
                            )
                        }
                    },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    singleLine = true
                )

                if (state.availableModels.isNotEmpty()) {
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        state.availableModels.forEach { modelId ->
                            DropdownMenuItem(
                                text = { 
                                    Column {
                                        Text(modelId, style = MaterialTheme.typography.bodyLarge)
                                        if (modelId == state.modelName) {
                                            Text("Currently Selected", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                },
                                onClick = {
                                    viewModel.updateModelName(modelId)
                                    expanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
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

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { viewModel.saveSettings() },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(imageVector = Icons.Rounded.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Server Configuration")
            }
        }
    }
}
