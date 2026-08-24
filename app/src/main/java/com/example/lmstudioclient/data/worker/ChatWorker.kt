package com.example.lmstudioclient.data.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.example.lmstudioclient.LMStudioApplication
import com.example.lmstudioclient.util.NetworkResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChatWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val sessionId = inputData.getString(KEY_SESSION_ID) ?: return@withContext Result.failure()
        val userPrompt = inputData.getString(KEY_USER_PROMPT) ?: ""
        val attachmentPaths = inputData.getStringArray(KEY_ATTACHMENTS)?.toList() ?: emptyList()

        val app = applicationContext as LMStudioApplication
        val repository = app.repository

        setForeground(createForegroundInfo())

        val result = repository.sendMessage(
            sessionId = sessionId,
            userPrompt = userPrompt,
            attachmentPaths = attachmentPaths
        )

        when (result) {
            is NetworkResult.Success -> Result.success()
            is NetworkResult.Error -> {
                val outputData = workDataOf(KEY_ERROR_MESSAGE to result.message)
                Result.failure(outputData)
            }
            else -> Result.failure()
        }
    }

    private fun createForegroundInfo(): ForegroundInfo {
        val channelId = "chat_generation_channel"
        val notificationId = 101

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Chat Generation"
            val descriptionText = "Shows progress of LLM response generation"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("HomeAndI is Thinking")
            .setContentText("Generating response...")
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setOngoing(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    companion object {
        const val KEY_SESSION_ID = "session_id"
        const val KEY_USER_PROMPT = "user_prompt"
        const val KEY_ATTACHMENTS = "attachments"
        const val KEY_ERROR_MESSAGE = "error_message"

        fun enqueue(context: Context, sessionId: String, userPrompt: String, attachments: List<String>) {
            val data = workDataOf(
                KEY_SESSION_ID to sessionId,
                KEY_USER_PROMPT to userPrompt,
                KEY_ATTACHMENTS to attachments.toTypedArray()
            )

            val request = OneTimeWorkRequestBuilder<ChatWorker>()
                .setInputData(data)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "send_message_$sessionId",
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request
            )
        }
    }
}
