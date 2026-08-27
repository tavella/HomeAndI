package com.example.lmstudioclient.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

import retrofit2.http.Path

interface LMStudioApiService {

    @POST("v1/chat/completions")
    suspend fun createChatCompletion(
        @Body request: ChatCompletionRequest
    ): Response<ChatCompletionResponse>

    @GET("v1/models")
    suspend fun getModels(): Response<ModelListResponse>

    @POST("api/v1/models/load")
    suspend fun loadModelLmStudio(
        @Body request: ModelLoadRequest
    ): Response<Unit>

    @POST("api/v1/models/unload")
    suspend fun unloadModelLmStudio(
        @Body request: ModelUnloadRequest
    ): Response<Unit>

    @POST("admin/api/models/{model_id}/load")
    suspend fun loadModelOmlx(
        @Path("model_id") modelId: String
    ): Response<Unit>

    @POST("admin/api/models/{model_id}/unload")
    suspend fun unloadModelOmlx(
        @Path("model_id") modelId: String
    ): Response<Unit>

    @GET("v1/models/status")
    suspend fun getModelsStatus(): Response<com.google.gson.JsonElement>
}
