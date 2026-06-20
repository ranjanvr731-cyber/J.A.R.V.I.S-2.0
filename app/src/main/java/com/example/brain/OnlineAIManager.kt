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
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class OnlineAIManager {
    private val TAG = "JarvisOnlineAIManager"

    fun isRealTimeQueryNeeded(query: String): Boolean {
        val lower = query.lowercase(java.util.Locale.US).trim()
        val keywords = listOf(
            "news", "headline", "latest", "update", "current", "weather", 
            "temperature", "rain", "forecast", "sport", "score", "match", 
            "live", "today", "yesterday", "now", "who is the current", 
            "who is president", "standing", "versus", "vs", "stock", "price"
        )
        return keywords.any { lower.contains(it) }
    }

    suspend fun performWebSearch(query: String): String = withContext(Dispatchers.IO) {
        val searchResults = StringBuilder()
        val encodedQuery = try {
            java.net.URLEncoder.encode(query, "UTF-8")
        } catch (e: Exception) {
            query
        }
        
        // 1. Try DuckDuckGo Instant Answer API for extremely quick, lightweight news or definitions
        val ddgUrlStr = "https://api.duckduckgo.com/?q=$encodedQuery&format=json&no_html=1"
        try {
            val url = URL(ddgUrlStr)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            connection.requestMethod = "GET"
            
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val abstractText = json.optString("AbstractText")
                if (abstractText.isNotEmpty()) {
                    searchResults.append("Recent news / instant recap:\n- DDG Summary: $abstractText\n\n")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "DuckDuckGo web lookup bypassed: ${e.localizedMessage}")
        }
        
        // 2. Query Wikipedia Search API for structural/live events or context
        val wikiUrlStr = "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encodedQuery&format=json&utf8=1"
        try {
            val url = URL(wikiUrlStr)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 3500
            connection.readTimeout = 3500
            connection.requestMethod = "GET"
            
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val queryObj = json.optJSONObject("query")
                if (queryObj != null) {
                    val searchArr = queryObj.optJSONArray("search")
                    if (searchArr != null && searchArr.length() > 0) {
                        if (searchResults.isEmpty()) {
                            searchResults.append("Recent Web Information Grounding:\n")
                        } else {
                            searchResults.append("Supporting Context Found:\n")
                        }
                        for (i in 0 until Math.min(searchArr.length(), 3)) {
                            val item = searchArr.getJSONObject(i)
                            val title = item.optString("title")
                            val snippet = item.optString("snippet").replace(Regex("<[^>]*>"), "")
                            searchResults.append("- $title: $snippet\n")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Wikipedia search query failed: ${e.localizedMessage}")
        }
        
        searchResults.toString()
    }

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
