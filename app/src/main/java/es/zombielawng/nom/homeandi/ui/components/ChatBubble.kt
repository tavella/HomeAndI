package es.zombielawng.nom.homeandi.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import es.zombielawng.nom.homeandi.data.local.ChatMessageEntity
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

                    // Rich Text & Markdown Rendering
                    MarkdownMessage(
                        text = message.content,
                        contentColor = contentColor
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

@Composable
fun MarkdownMessage(
    text: String,
    contentColor: Color
) {
    val parts = remember(text) { text.split("```") }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (i in parts.indices) {
            val part = parts[i]
            if (i % 2 == 1) {
                // Code block
                val lines = part.split("\n")
                val hasLang = lines.firstOrNull()?.trim()?.matches(Regex("^[a-zA-Z\\+\\#]+$")) ?: false
                val codeText = if (hasLang) {
                    lines.drop(1).joinToString("\n").trim()
                } else {
                    part.trim()
                }

                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = codeText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = contentColor,
                        modifier = Modifier
                            .padding(12.dp)
                            .horizontalScroll(rememberScrollState())
                    )
                }
            } else {
                // Standard markdown text
                if (part.isNotBlank()) {
                    val annotatedString = remember(part) { parseMarkdown(part.trim()) }
                    Text(
                        text = annotatedString,
                        style = MaterialTheme.typography.bodyLarge,
                        color = contentColor
                    )
                }
            }
        }
    }
}

fun parseMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    val lines = text.split("\n")
    for ((index, line) in lines.withIndex()) {
        if (index > 0) {
            append("\n")
        }

        when {
            line.startsWith("# ") -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 21.sp)) {
                    append(parseInlineMarkdown(line.substring(2)))
                }
            }
            line.startsWith("## ") -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp)) {
                    append(parseInlineMarkdown(line.substring(3)))
                }
            }
            line.startsWith("### ") -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp)) {
                    append(parseInlineMarkdown(line.substring(4)))
                }
            }
            line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> {
                val indent = line.takeWhile { it.isWhitespace() }
                append(indent)
                append("• ")
                val content = line.trimStart().substring(2)
                append(parseInlineMarkdown(content))
            }
            else -> {
                append(parseInlineMarkdown(line))
            }
        }
    }
}

private fun parseInlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) && text.indexOf("**", i + 2) != -1 -> {
                val end = text.indexOf("**", i + 2)
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(text.substring(i + 2, end))
                }
                i = end + 2
            }
            text.startsWith("*", i) && text.indexOf("*", i + 1) != -1 -> {
                val end = text.indexOf("*", i + 1)
                withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                    append(text.substring(i + 1, end))
                }
                i = end + 1
            }
            text.startsWith("`", i) && text.indexOf("`", i + 1) != -1 -> {
                val end = text.indexOf("`", i + 1)
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color.LightGray.copy(alpha = 0.2f))) {
                    append(text.substring(i + 1, end))
                }
                i = end + 1
            }
            text.startsWith("<b>", i) && text.indexOf("</b>", i + 3) != -1 -> {
                val end = text.indexOf("</b>", i + 3)
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(text.substring(i + 3, end))
                }
                i = end + 5
            }
            text.startsWith("<i>", i) && text.indexOf("</i>", i + 3) != -1 -> {
                val end = text.indexOf("</i>", i + 3)
                withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                    append(text.substring(i + 3, end))
                }
                i = end + 5
            }
            text.startsWith("<code>", i) && text.indexOf("</code>", i + 6) != -1 -> {
                val end = text.indexOf("</code>", i + 6)
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color.LightGray.copy(alpha = 0.2f))) {
                    append(text.substring(i + 6, end))
                }
                i = end + 7
            }
            else -> {
                append(text[i].toString())
                i++
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
