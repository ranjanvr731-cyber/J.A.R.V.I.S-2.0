package com.example.brain

import android.util.Log
import com.example.data.network.Content
import com.example.data.network.GenerateContentRequest
import com.example.data.network.GenerationConfig
import com.example.data.network.InlineData
import com.example.data.network.Part
import com.example.data.network.RetrofitClient
import com.example.data.network.GoogleApiKeyProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OnlineAIManager {
    private val TAG = "JarvisOnlineAIManager"

    // Execute standard text content generation
    suspend fun generateText(prompt: String, systemInstruction: String? = null): String = withContext(Dispatchers.IO) {
        val apiKey = GoogleApiKeyProvider.getApiKey()
        if (apiKey.isBlank()) {
            return@withContext "Error: Gemini API Key is missing. Please add your GEMINI_API_KEY securely into the Secrets panel in AI Studio."
        }

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            systemInstruction = systemInstruction?.let { Content(parts = listOf(Part(text = it))) },
            generationConfig = GenerationConfig(temperature = 0.7)
        )

        try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text 
                ?: "Empty response received from cognitive neural lines, Bro."
        } catch (e: Exception) {
            Log.e(TAG, "Online API call failed", e)
            "Error matching context online: ${e.localizedMessage}. Checking local protocols..."
        }
    }

    // Capture vision prompts (OCR or Scene explanation)
    suspend fun generateVisionText(prompt: String, base64Image: String, mimeType: String = "image/jpeg"): String = withContext(Dispatchers.IO) {
        val apiKey = GoogleApiKeyProvider.getApiKey()
        if (apiKey.isBlank()) {
            return@withContext "Error: Vision module requires a functional API Key. Please insert it in Secrets."
        }

        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(text = prompt),
                        Part(inlineData = InlineData(mimeType = mimeType, data = base64Image))
                    )
                )
            ),
            generationConfig = GenerationConfig(temperature = 0.4)
        )

        try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "Mute visual response received. Image scanner active but empty."
        } catch (e: Exception) {
            Log.e(TAG, "Vision API call failed", e)
            "Error analyzing image sensor: ${e.localizedMessage}"
        }
    }
}
