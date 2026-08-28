package es.zombielawng.nom.homeandi.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

import retrofit2.http.Path

interface HomeAndIApiService {

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
        @Path(value = "model_id", encoded = true) modelId: String
    ): Response<Unit>

    @POST("admin/api/models/{model_id}/unload")
    suspend fun unloadModelOmlx(
        @Path(value = "model_id", encoded = true) modelId: String
    ): Response<Unit>

    @GET("v1/models/status")
    suspend fun getModelsStatus(): Response<com.google.gson.JsonElement>

    @retrofit2.http.DELETE("admin/api/models/{model_id}")
    suspend fun deleteModelOmlx(
        @Path(value = "model_id", encoded = true) modelId: String
    ): Response<Unit>

    @retrofit2.http.DELETE("v1/models/{model_id}")
    suspend fun deleteModel(
        @Path(value = "model_id", encoded = true) modelId: String
    ): Response<Unit>

    @POST("https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @retrofit2.http.Query("key") apiKey: String,
        @Body request: GeminiGenerateContentRequest
    ): Response<GeminiGenerateContentResponse>

    @GET("https://generativelanguage.googleapis.com/v1beta/models")
    suspend fun getGeminiModels(
        @retrofit2.http.Query("key") apiKey: String
    ): Response<GeminiModelListResponse>

}
