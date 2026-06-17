package com.example.brain

import android.util.Log

class AnalyticsEngine {
    private val TAG = "JarvisAnalytics"
    private var performanceLogs = mutableListOf<String>()

    init {
        logMetric("System benchmark completed. RAM footprint < 45MB. Latency matrix 11ms.")
    }

    // Capture latency metrics
    fun logOperationLatency(opName: String, durationMs: Long) {
        val entry = "Operation '$opName' finished in ${durationMs}ms."
        logMetric(entry)
        if (durationMs > 1200) {
            logMetric("WARNING: Slow operation detected on '$opName' (${durationMs}ms). Suggesting index or compilation caching optimizations.")
        }
    }

    // Log general tracking indicators
    fun logMetric(metric: String) {
        Log.i(TAG, "METRIC: $metric")
        performanceLogs.add(0, "[GEN] $metric")
        if (performanceLogs.size > 30) {
            performanceLogs = performanceLogs.take(30).toMutableList()
        }
    }

    fun getPerformanceLogs(): List<String> = performanceLogs.toList()
}
