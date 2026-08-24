package com.example.lmstudioclient.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface LMStudioApiService {

    @POST("v1/chat/completions")
    suspend fun createChatCompletion(
        @Body request: ChatCompletionRequest
    ): Response<ChatCompletionResponse>

    @GET("api/v1/models")
    suspend fun getModels(): Response<ModelListResponse>

    @POST("api/v1/models/load")
    suspend fun loadModel(
        @Body request: ModelLoadRequest
    ): Response<Unit>

    @POST("api/v1/models/unload")
    suspend fun unloadModel(
        @Body request: ModelUnloadRequest
    ): Response<Unit>
}
