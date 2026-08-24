package com.example.lmstudioclient.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.lmstudioclient.data.local.ChatMessageEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatBubble(
    message: ChatMessageEntity,
    onAttachmentClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isUser = message.role.lowercase() == "user"
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart

    val bubbleShape = if (isUser) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    }

    val bubbleBg = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val formattedTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
    val attachments = parseAttachments(message.attachmentPathsJson)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 12.dp),
        contentAlignment = alignment
    ) {
        Row(
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            if (!isUser) {
                Surface(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Android,
                        contentDescription = "Assistant Icon",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(6.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Surface(
                shape = bubbleShape,
                color = bubbleBg,
                shadowElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    if (attachments.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            items(attachments) { path ->
                                AsyncImage(
                                    model = path,
                                    contentDescription = "Attachment Thumbnail",
                                    modifier = Modifier
                                        .size(100.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onAttachmentClick(path) },
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }

                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = contentColor
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        if (message.status == "ERROR") {
                            Icon(
                                imageVector = Icons.Rounded.Error,
                                contentDescription = "Error Sending",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        Text(
                            text = formattedTime,
                            fontSize = 10.sp,
                            color = contentColor.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            if (isUser) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = "User Icon",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(6.dp)
                    )
                }
            }
        }
    }
}

private fun parseAttachments(json: String): List<String> {
    return try {
        val type = object : TypeToken<List<String>>() {}.type
        Gson().fromJson(json, type) ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }
}
