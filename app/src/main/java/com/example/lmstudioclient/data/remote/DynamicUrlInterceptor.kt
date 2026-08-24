package com.example.lmstudioclient.data.remote

import com.example.lmstudioclient.data.preferences.ServerPreferencesManager
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

        val targetHost = preferencesManager.getHostSync()
        val targetPort = preferencesManager.getPortSync()
        val targetScheme = preferencesManager.getSchemeSync()

        val newUrl = originalRequest.url.newBuilder()
            .scheme(targetScheme)
            .host(targetHost)
            .port(targetPort)
            .build()

        val newRequest = originalRequest.newBuilder()
            .url(newUrl)
            .build()

        return chain.proceed(newRequest)
    }
}
