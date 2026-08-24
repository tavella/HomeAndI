package com.example.lmstudioclient.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.lmstudioclient.data.local.ChatSessionEntity

@Composable
fun SessionDrawerContent(
    sessions: List<ChatSessionEntity>,
    activeSessionId: String?,
    onSelectSession: (String) -> Unit,
    onNewSession: () -> Unit,
    onDeleteSession: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalDrawerSheet(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "HomeAndI Sessions",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Settings"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // New Chat Button
            Button(
                onClick = onNewSession,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(imageVector = Icons.Rounded.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("New Chat Session")
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider()
            Spacer(modifier = Modifier.height(8.dp))

            // Session List
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(sessions, key = { it.id }) { session ->
                    val isSelected = session.id == activeSessionId
                    NavigationDrawerItem(
                        label = {
                            Text(
                                text = session.title,
                                maxLines = 1,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        selected = isSelected,
                        onClick = { onSelectSession(session.id) },
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.ChatBubbleOutline,
                                contentDescription = null
                            )
                        },
                        badge = {
                            IconButton(onClick = { onDeleteSession(session.id) }) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Delete Session",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        },
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}
