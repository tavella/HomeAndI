package es.zombielawng.nom.homeandi.data.remote

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
    @SerializedName("models") val models: List<ModelData>? = null,
    @SerializedName("data") val data: List<ModelData>? = null
)

data class ModelData(
    @SerializedName("key") private val _key: String? = null,
    @SerializedName("id") private val _id: String? = null,
    @SerializedName("display_name") val displayName: String? = null,
    @SerializedName("format") val format: String? = null,
    @SerializedName("architecture") val architecture: String? = null,
    @SerializedName("size_bytes") val sizeBytes: Long? = null,
    @SerializedName("max_context_length") val maxContextLength: Int? = null,
    @SerializedName("loaded_instances") val loadedInstances: List<LoadedInstance>? = null,
    val _isOmlxLoaded: Boolean? = null
) {
    constructor(
        id: String,
        displayName: String? = null,
        format: String? = null,
        architecture: String? = null,
        sizeBytes: Long? = null,
        maxContextLength: Int? = null,
        loadedInstances: List<LoadedInstance>? = null
    ) : this(
        _key = id,
        _id = null,
        displayName = displayName,
        format = format,
        architecture = architecture,
        sizeBytes = sizeBytes,
        maxContextLength = maxContextLength,
        loadedInstances = loadedInstances,
        _isOmlxLoaded = null
    )

    val id: String
        get() = _id ?: _key ?: ""

    val isLoaded: Boolean
        get() = if (_isOmlxLoaded != null) _isOmlxLoaded else (!loadedInstances.isNullOrEmpty() || _id != null)

    val supportsManagement: Boolean
        get() = true // Set to true to allow showing the table for all backends
}

data class LoadedInstance(
    @SerializedName("id") val id: String,
    @SerializedName("config") val config: Map<String, Any>? = null
)

data class ModelLoadRequest(
    @SerializedName("model") val model: String
)

data class ModelUnloadRequest(
    @SerializedName("instance_id") val instanceId: String
)

data class OmlxModelLoadRequest(
    @SerializedName("model_path") val modelPath: String,
    @SerializedName("gguf_variant") val ggufVariant: String? = null
)

data class OmlxModelUnloadRequest(
    @SerializedName("model_path") val modelPath: String
)
