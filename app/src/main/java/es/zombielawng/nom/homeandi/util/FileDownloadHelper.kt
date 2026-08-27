package es.zombielawng.nom.homeandi.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object FileDownloadHelper {

    suspend fun saveAttachmentToDownloads(context: Context, path: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(path)
            val fileName = getFileName(path)
            val mimeType = getMimeType(path)

            val inputStream = getInputStream(context, uri) ?: return@withContext false

            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
            }

            val targetUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, fileName)
                Uri.fromFile(file)
            }

            if (targetUri != null) {
                val outputStream = if (targetUri.scheme == "file") {
                    FileOutputStream(File(targetUri.path!!))
                } else {
                    resolver.openOutputStream(targetUri)
                }
                outputStream?.use { out ->
                    inputStream.use { inp ->
                        inp.copyTo(out)
                    }
                }
                return@withContext true
            }
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun saveAttachmentToGallery(context: Context, path: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(path)
            val fileName = getFileName(path)
            val mimeType = getMimeType(path)
            val isVideo = mimeType?.startsWith("video") == true

            val inputStream = getInputStream(context, uri) ?: return@withContext false

            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val relativePath = if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                }
            }

            val collectionUri = if (isVideo) {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            val targetUri = resolver.insert(collectionUri, contentValues)
            if (targetUri != null) {
                resolver.openOutputStream(targetUri)?.use { out ->
                    inputStream.use { inp ->
                        inp.copyTo(out)
                    }
                }
                return@withContext true
            }
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun getInputStream(context: Context, uri: Uri): InputStream? {
        return when (uri.scheme) {
            "http", "https" -> {
                val connection = URL(uri.toString()).openConnection() as HttpURLConnection
                connection.connect()
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    connection.inputStream
                } else {
                    null
                }
            }
            "content" -> {
                context.contentResolver.openInputStream(uri)
            }
            else -> {
                val file = File(uri.path ?: uri.toString())
                if (file.exists()) file.inputStream() else null
            }
        }
    }

    fun getFileName(path: String): String {
        val lastSegment = path.substringAfterLast('/')
        val nameWithoutParams = lastSegment.substringBefore('?')
        return if (nameWithoutParams.isNotBlank()) {
            nameWithoutParams
        } else {
            "attachment_${System.currentTimeMillis()}"
        }
    }

    fun getMimeType(path: String): String? {
        val extension = MimeTypeMap.getFileExtensionFromUrl(path)
            ?: path.substringAfterLast('.', "").substringBefore('?')
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
            ?: when (extension.lowercase()) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                "mp4" -> "video/mp4"
                "mov" -> "video/quicktime"
                "pdf" -> "application/pdf"
                "txt" -> "text/plain"
                else -> "*/*"
            }
    }
}
