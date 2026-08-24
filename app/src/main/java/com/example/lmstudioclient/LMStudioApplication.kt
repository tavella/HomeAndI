package com.example.lmstudioclient

import android.app.Application
import com.example.lmstudioclient.data.local.AppDatabase
import com.example.lmstudioclient.data.preferences.ServerPreferencesManager
import com.example.lmstudioclient.data.remote.DynamicUrlInterceptor
import com.example.lmstudioclient.data.remote.LMStudioApiService
import com.example.lmstudioclient.data.repository.ChatRepository
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class LMStudioApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var preferencesManager: ServerPreferencesManager
        private set

    lateinit var dynamicUrlInterceptor: DynamicUrlInterceptor
        private set

    lateinit var apiService: LMStudioApiService
        private set

    lateinit var repository: ChatRepository
        private set

    override fun onCreate() {
        super.onCreate()

        // 1. Initialize Room Database
        database = AppDatabase.getDatabase(this)

        // 2. Initialize Preferences Manager
        preferencesManager = ServerPreferencesManager(this)

        // 3. Initialize Dynamic OkHttp Interceptor
        dynamicUrlInterceptor = DynamicUrlInterceptor(preferencesManager)

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(dynamicUrlInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.MINUTES) // Increased for slow LLM generation
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        // 4. Initialize Retrofit with dummy base URL (overridden dynamically by interceptor)
        val retrofit = Retrofit.Builder()
            .baseUrl("http://localhost:1234/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit.create(LMStudioApiService::class.java)

        // 5. Initialize Repository
        repository = ChatRepository(
            chatSessionDao = database.chatSessionDao(),
            chatMessageDao = database.chatMessageDao(),
            apiService = apiService,
            preferencesManager = preferencesManager,
            context = this
        )
    }
}
