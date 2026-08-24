package com.example.lmstudioclient.data.remote

import com.google.gson.annotations.SerializedName

/**
 * OpenAI Chat Completions Request Payload
 */
data class ChatCompletionRequest(
    @SerializedName("model") val model: String,
    @SerializedName("messages") val messages: List<ApiMessage>,
    @SerializedName("temperature") val temperature: Double = 0.7,
    @SerializedName("max_tokens") val maxTokens: Int? = null,
    @SerializedName("stream") val stream: Boolean = false
)

/**
 * Message object within API request payload.
 * [content] can be a String or a List<ContentPart> for multimodal input.
 */
data class ApiMessage(
    @SerializedName("role") val role: String,
    @SerializedName("content") val content: Any
)

/**
 * Text Content Part for Multimodal Prompts
 */
data class TextContentPart(
    @SerializedName("type") val type: String = "text",
    @SerializedName("text") val text: String
)

/**
 * Image Content Part for Multimodal Prompts (OpenAI Vision standard)
 */
data class ImageUrlContentPart(
    @SerializedName("type") val type: String = "image_url",
    @SerializedName("image_url") val imageUrl: ImageUrlDetail
)

data class ImageUrlDetail(
    @SerializedName("url") val url: String // Data URI scheme: data:image/png;base64,...
)

/**
 * OpenAI Chat Completion Response Payload
 */
data class ChatCompletionResponse(
    @SerializedName("id") val id: String?,
    @SerializedName("created") val created: Long?,
    @SerializedName("model") val model: String?,
    @SerializedName("choices") val choices: List<Choice>?,
    @SerializedName("usage") val usage: Usage?
)

data class Choice(
    @SerializedName("index") val index: Int,
    @SerializedName("message") val message: ResponseMessage,
    @SerializedName("finish_reason") val finishReason: String?
)

data class ResponseMessage(
    @SerializedName("role") val role: String,
    @SerializedName("content") val content: String?
)

data class Usage(
    @SerializedName("prompt_tokens") val promptTokens: Int,
    @SerializedName("completion_tokens") val completionTokens: Int,
    @SerializedName("total_tokens") val totalTokens: Int
)

/**
 * Model Discovery / Ping Response Payload
 */
data class ModelListResponse(
    @SerializedName("data") val data: List<ModelData>?
)

data class ModelData(
    @SerializedName("id") val id: String,
    @SerializedName("owned_by") val ownedBy: String? = null
)
