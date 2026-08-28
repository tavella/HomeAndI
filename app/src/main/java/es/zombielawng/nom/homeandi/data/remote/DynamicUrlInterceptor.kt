package es.zombielawng.nom.homeandi.data.remote

import es.zombielawng.nom.homeandi.data.preferences.ServerPreferencesManager
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Dynamic OkHttp Interceptor that rewrites outgoing request host, port, and scheme
 * based on current ServerPreferencesManager configuration.
 */
class DynamicUrlInterceptor(
    private val preferencesManager: ServerPreferencesManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val originalUrl = originalRequest.url



        val targetHost = preferencesManager.getHostSync()
        val targetPort = preferencesManager.getPortSync()
        val targetScheme = preferencesManager.getSchemeSync()

        val newUrl = originalRequest.url.newBuilder()
            .scheme(targetScheme)
            .host(targetHost)
            .port(targetPort)
            .build()

        val apiKey = preferencesManager.getApiKeySync()

        val newRequest = originalRequest.newBuilder()
            .url(newUrl)
            // Attach the configured API key (e.g. for OpenAI-compatible endpoints)
            .apply {
                if (apiKey.isNotBlank()) {
                    header("Authorization", "Bearer $apiKey")
                    header("x-goog-api-key", apiKey)
                }
            }
            .build()

        return chain.proceed(newRequest)
    }
}
