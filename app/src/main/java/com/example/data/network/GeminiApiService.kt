package com.example.data.network

import android.util.Log
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.io.IOException
import java.util.concurrent.TimeUnit
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

class RateLimitingAndBackoffInterceptor : Interceptor {
    private var lastRequestTime = 0L
    private val minIntervalMs = 1500L // Ensure 1.5 seconds minimum layout spacing between parallel requests

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // 1. Client-Side Rate Limiter (Token/Spacing)
        synchronized(this) {
            val now = System.currentTimeMillis()
            val timeSinceLast = now - lastRequestTime
            if (timeSinceLast < minIntervalMs) {
                val delayTime = minIntervalMs - timeSinceLast
                try {
                    Log.d("JARVIS_RATE_LIMIT", "Delaying prompt request by ${delayTime}ms to prevent HTTP 429 spikes")
                    Thread.sleep(delayTime)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
            lastRequestTime = System.currentTimeMillis()
        }

        // 2. Exponential Backoff Policy for HTTP 429 (Too Many Requests), 439, or transient errors
        var response: Response? = null
        var attempt = 0
        val maxAttempts = 4
        var backoffDelay = 1500L // Starts at 1.5s delay

        while (attempt < maxAttempts) {
            try {
                response?.close()
                response = chain.proceed(request)
                val code = response.code

                if (code == 429 || code == 408 || code == 439 || code >= 500) {
                    attempt++
                    Log.w("JARVIS_BACKOFF", "Network congestion detected (HTTP Code $code). Retry execution $attempt of $maxAttempts. Delaying ${backoffDelay}ms.")
                    if (attempt < maxAttempts) {
                        try {
                            Thread.sleep(backoffDelay)
                        } catch (ie: InterruptedException) {
                            Thread.currentThread().interrupt()
                        }
                        backoffDelay *= 2 // Exponential multiplier
                        continue
                    }
                }
                break // Success or exhausted maximum attempt limits
            } catch (e: Exception) {
                attempt++
                Log.e("JARVIS_BACKOFF", "Physical routing connection exception (Attempt $attempt/$maxAttempts)", e)
                if (attempt < maxAttempts) {
                    try {
                        Thread.sleep(backoffDelay)
                    } catch (ie: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                    backoffDelay *= 2
                    continue
                } else {
                    throw IOException("Connection failed after multiple retries with backoff strategy: ${e.localizedMessage}", e)
                }
            }
        }
        return response ?: throw IOException("Unresolved communication core failure after rate limiting retry exhaustion.")
    }
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(RateLimitingAndBackoffInterceptor())
        .build()

    val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }
}

object GoogleApiKeyProvider {
    fun getApiKey(): String {
        val targets = listOf(
            com.example.BuildConfig.GEMINI_API_KEY,
            System.getenv("GEMINI_API_KEY") ?: "",
            System.getProperty("GEMINI_API_KEY") ?: "",
            System.getenv("api_key") ?: "",
            System.getProperty("api_key") ?: ""
        )
        for (candidate in targets) {
            if (candidate.isNotBlank() && candidate != "MY_GEMINI_API_KEY" && candidate != "null") {
                return candidate.trim()
            }
        }
        return ""
    }
}
