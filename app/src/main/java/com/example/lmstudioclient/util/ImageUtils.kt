package com.example.lmstudioclient.util

import android.content.Context
import android.net.Uri
import android.util.Base64
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

object ImageUtils {

    /**
     * Converts a local file path or Content URI into a Base64 Data URI string formatted for
     * OpenAI Multimodal Vision APIs (e.g. "data:image/jpeg;base64,...").
     */
    fun pathToBase64DataUrl(context: Context?, pathOrUriString: String): String {
        return try {
            val inputStream: InputStream? = when {
                pathOrUriString.startsWith("content://") && context != null -> {
                    context.contentResolver.openInputStream(Uri.parse(pathOrUriString))
                }
                else -> {
                    val file = File(pathOrUriString)
                    if (file.exists()) FileInputStream(file) else null
                }
            }

            val bytes = inputStream?.use { it.readBytes() } ?: return pathOrUriString
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val mimeType = detectMimeType(pathOrUriString)
            "data:$mimeType;base64,$base64"
        } catch (e: Exception) {
            e.printStackTrace()
            pathOrUriString
        }
    }

    private fun detectMimeType(path: String): String {
        val lower = path.lowercase()
        return when {
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".gif") -> "image/gif"
            else -> "image/jpeg"
        }
    }
}
