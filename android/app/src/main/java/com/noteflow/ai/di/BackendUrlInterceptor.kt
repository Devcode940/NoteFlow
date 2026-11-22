package com.noteflow.ai.di

import com.noteflow.ai.BuildConfig
import com.noteflow.ai.core.common.Logger
import com.noteflow.ai.domain.repository.PreferencesRepository
import dagger.Lazy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rewrites AI-backend requests to the user-configured backend URL from
 * settings, so non-streaming (Retrofit) and streaming (raw OkHttp) calls
 * always target the same host.
 *
 * Only requests aimed at the compile-time default backend are rewritten —
 * Supabase and other hosts pass through untouched. In release builds a
 * non-HTTPS backend URL is rejected outright: the Firebase ID token must
 * never travel over plaintext HTTP.
 */
@Singleton
class BackendUrlInterceptor @Inject constructor(
    private val preferencesRepository: Lazy<PreferencesRepository>
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val defaultBase = BuildConfig.BACKEND_URL.toHttpUrlOrNull()
            ?: return chain.proceed(request)
        val targetsDefault = request.url.host == defaultBase.host &&
            request.url.port == defaultBase.port
        if (!targetsDefault) return chain.proceed(request)

        val configured = runCatching {
            runBlocking { preferencesRepository.get().settings.first().backendUrl }
        }.getOrNull().orEmpty().trim()
        if (configured.isEmpty()) return chain.proceed(request)

        val configuredBase = configured.toHttpUrlOrNull()
            ?: throw IOException("Invalid AI backend URL configured in settings")
        if (!BuildConfig.DEBUG && configuredBase.scheme != "https") {
            throw IOException("HTTPS is required for the AI backend in release builds")
        }
        val rewritten = configuredBase.newBuilder()
            .addEncodedPathSegments(request.url.encodedPath.removePrefix("/"))
            .query(request.url.query)
            .build()
        Logger.d("BackendUrl", "Rewriting AI request to configured backend")
        return chain.proceed(request.newBuilder().url(rewritten).build())
    }
}
