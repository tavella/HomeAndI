package es.zombielawng.nom.homeandi.data

import es.zombielawng.nom.homeandi.data.remote.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@OptIn(ExperimentalCoroutinesApi::class)
class HomeAndIApiServiceTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiService: HomeAndIApiService

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        apiService = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(HomeAndIApiService::class.java)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `createChatCompletion sends correct OpenAI JSON request to v1-chat-completions`() = runTest {
        val jsonResponse = """
            {
              "id": "chatcmpl-123",
              "created": 1677652288,
              "model": "meta-llama-3-8b-instruct",
              "choices": [{
                "index": 0,
                "message": {
                  "role": "assistant",
                  "content": "Hello! How can I assist you today?"
                },
                "finish_reason": "stop"
              }],
              "usage": {
                "prompt_tokens": 9,
                "completion_tokens": 12,
                "total_tokens": 21
              }
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(jsonResponse))

        val requestPayload = ChatCompletionRequest(
            model = "meta-llama-3-8b-instruct",
            messages = listOf(
                ApiMessage(role = "system", content = "You are a helpful assistant."),
                ApiMessage(role = "user", content = "Hello")
            ),
            temperature = 0.7
        )

        val response = apiService.createChatCompletion(requestPayload)

        // Verify request line and path
        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("POST", recordedRequest.method)
        assertEquals("/v1/chat/completions", recordedRequest.path)

        // Verify JSON body payload sent to server
        val bodyText = recordedRequest.body.readUtf8()
        assertTrue(bodyText.contains("meta-llama-3-8b-instruct"))
        assertTrue(bodyText.contains("You are a helpful assistant."))
        assertTrue(bodyText.contains("\"role\":\"user\""))

        // Verify response parsing
        assertTrue(response.isSuccessful)
        assertNotNull(response.body())
        val choice = response.body()!!.choices?.first()
        assertEquals("assistant", choice?.message?.role)
        assertEquals("Hello! How can I assist you today?", choice?.message?.content)
    }

    @Test
    fun `createChatCompletion supports multimodal vision payload structure`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val multimodalParts = listOf(
            TextContentPart(text = "What is in this image?"),
            ImageUrlContentPart(imageUrl = ImageUrlDetail(url = "data:image/jpeg;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="))
        )

        val requestPayload = ChatCompletionRequest(
            model = "llava-v1.6-7b",
            messages = listOf(
                ApiMessage(role = "user", content = multimodalParts)
            )
        )

        apiService.createChatCompletion(requestPayload)

        val recordedRequest = mockWebServer.takeRequest()
        val bodyText = recordedRequest.body.readUtf8()

        assertTrue(bodyText.contains("\"type\":\"text\""))
        assertTrue(bodyText.contains("\"type\":\"image_url\""))
        assertTrue(bodyText.contains("data:image/jpeg;base64,"))
    }

    @Test
    fun `generateContent sends correct Gemini request payload and parses response groundingMetadata correctly`() = runTest {
        val mockResponseJson = """
            {
              "candidates": [
                {
                  "content": {
                    "role": "model",
                    "parts": [{ "text": "Here is the grounded response content" }]
                  },
                  "finishReason": "STOP",
                  "groundingMetadata": {
                    "webSearchQueries": ["grounding search query"],
                    "groundingChunks": [
                      { "web": { "uri": "https://grounding.example.com", "title": "Example Grounding Source" } }
                    ]
                  }
                }
              ],
              "usageMetadata": {
                "promptTokenCount": 12,
                "candidatesTokenCount": 20,
                "totalTokenCount": 32
              }
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(mockResponseJson))

        val requestPayload = GeminiGenerateContentRequest(
            contents = listOf(
                GeminiContent(role = "user", parts = listOf(GeminiPart(text = "Grounding test prompt")))
            ),
            tools = listOf(GeminiTool(googleSearch = emptyMap()))
        )

        val response = apiService.generateContent("gemini-1.5-flash", requestPayload)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("POST", recordedRequest.method)
        assertEquals("/v1beta/models/gemini-1.5-flash:generateContent", recordedRequest.path)

        val bodyText = recordedRequest.body.readUtf8()
        assertTrue(bodyText.contains("Grounding test prompt"))
        assertTrue(bodyText.contains("googleSearch"))

        assertTrue(response.isSuccessful)
        assertNotNull(response.body())
        val candidate = response.body()!!.candidates?.firstOrNull()
        assertNotNull(candidate)
        assertEquals("model", candidate?.content?.role)
        assertEquals("Here is the grounded response content", candidate?.content?.parts?.firstOrNull()?.text)
        
        val metadata = candidate?.groundingMetadata
        assertNotNull(metadata)
        assertEquals("https://grounding.example.com", metadata?.groundingChunks?.firstOrNull()?.web?.uri)
        assertEquals("Example Grounding Source", metadata?.groundingChunks?.firstOrNull()?.web?.title)
    }

    @Test
    fun `getModels parses v1-models endpoint correctly for connection test`() = runTest {
        val modelsJson = """
            {
              "models": [
                { "key": "meta-llama-3-8b-instruct", "display_name": "Llama 3" },
                { "key": "qwen2.5-coder-7b-instruct", "display_name": "Qwen 2.5" }
              ]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(modelsJson))

        val response = apiService.getModels()

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("GET", recordedRequest.method)
        assertEquals("/v1/models", recordedRequest.path)

        assertTrue(response.isSuccessful)
        val models = response.body()?.models
        assertNotNull(models)
        assertEquals(2, models!!.size)
        assertEquals("meta-llama-3-8b-instruct", models[0].id)
    }

    @Test
    fun `getModels parses standard OpenAI response format correctly`() = runTest {
        val modelsJson = """
            {
              "object": "list",
              "data": [
                { "id": "mlx-community/Llama-3-8B-Instruct-4bit", "object": "model", "created": 1686935002, "owned_by": "mlx" }
              ]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(modelsJson))

        val response = apiService.getModels()

        assertTrue(response.isSuccessful)
        val data = response.body()?.data
        assertNotNull(data)
        assertEquals(1, data!!.size)
        assertEquals("mlx-community/Llama-3-8B-Instruct-4bit", data[0].id)
        assertTrue(data[0].isLoaded)
        assertTrue(data[0].supportsManagement)
    }

    @Test
    fun `loadModel sends POST request to api-v1-models-load with correct body`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val modelId = "test-model-123"
        apiService.loadModelLmStudio(ModelLoadRequest(modelId))

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("POST", recordedRequest.method)
        assertEquals("/api/v1/models/load", recordedRequest.path)
        
        val bodyText = recordedRequest.body.readUtf8()
        assertTrue(bodyText.contains("\"model\":\"$modelId\""))
    }

    @Test
    fun `unloadModel sends POST request to api-v1-models-unload with correct body`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val instanceId = "test-instance-456"
        apiService.unloadModelLmStudio(ModelUnloadRequest(instanceId))

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("POST", recordedRequest.method)
        assertEquals("/api/v1/models/unload", recordedRequest.path)
        
        val bodyText = recordedRequest.body.readUtf8()
        assertTrue(bodyText.contains("\"instance_id\":\"$instanceId\""))
    }

    @Test
    fun `loadModelOmlx sends POST request to admin-api-models-id-load`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val modelId = "mlx-community/Llama-3-8B"
        apiService.loadModelOmlx(modelId)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("POST", recordedRequest.method)
        assertEquals("/admin/api/models/mlx-community/Llama-3-8B/load", recordedRequest.path)
    }

    @Test
    fun `unloadModelOmlx sends POST request to admin-api-models-id-unload`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val modelId = "mlx-community/Llama-3-8B"
        apiService.unloadModelOmlx(modelId)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("POST", recordedRequest.method)
        assertEquals("/admin/api/models/mlx-community/Llama-3-8B/unload", recordedRequest.path)
    }
}
