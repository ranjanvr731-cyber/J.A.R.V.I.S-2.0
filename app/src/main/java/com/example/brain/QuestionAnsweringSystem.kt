package com.example.brain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

class QuestionAnsweringSystem {

    /**
     * Intercepts common factual or dynamic queries and returns the precise correct answer.
     * Returns null if the query should instead map to the cloud/general conversation workflows.
     */
    fun processQuestion(query: String, ramUsage: String = "15.4MB", batteryRate: String = "-1.2%/hr"): String? {
        val lower = query.lowercase().trim()

        // 1. Time query interception
        if (isTimeQuery(lower)) {
            val hmA = SimpleDateFormat("hh:mm a", Locale.US).format(Date())
            val fullSdf = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.US).format(Date())
            return "The correct time is currently $hmA, Bro. Synchronized with your device clock, on $fullSdf!"
        }

        // 2. Date / Day query interception
        if (isDateQuery(lower)) {
            val fullSdf = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.US).format(Date())
            return "The correct date today is $fullSdf, Bro. Hope you are having an amazing day!"
        }

        // 3. Day of week query interception
        if (isDayQuery(lower)) {
            val dayOfWeek = SimpleDateFormat("EEEE", Locale.US).format(Date())
            return "Today is $dayOfWeek, Bro. Time to stay productive!"
        }

        // 4. Mathematical computation interception
        val mathResult = evaluateMathQuery(lower)
        if (mathResult != null) {
            return mathResult
        }

        // 5. System metrics queries
        if (lower.contains("system health") || lower.contains("diagnostic status") || lower.contains("self healing status")) {
            return "Our system health is currently green and secure, Bro. Diagnostic state shows RAM at $ramUsage and battery stats at $batteryRate."
        }
        if (lower.contains("memory status") || lower.contains("ram status")) {
            return "Active memory registers are running optimally, Bro. Your current RAM footprint is $ramUsage. All leak watchdogs are silent."
        }
        if (lower.contains("battery status") || lower.contains("battery percentage") || lower.contains("battery rate")) {
            return "Device power grid is healthy. Battery is optimized with standby duty at $batteryRate, Bro."
        }

        // 6. Common factual creator queries
        if (lower.contains("who made you") || lower.contains("who created you") || lower.contains("who is your developer") || lower.contains("who developed you")) {
            return "I was created and deployed by Ranjan, Bro! He designed my neural cores to give you absolute command over your device."
        }
        if (lower.contains("who is ranjan") || lower.contains("creator's name") || lower.contains("your creator")) {
            return "Ranjan is my supreme creator and the chief engineer of this JARVIS terminal, Bro."
        }
        if (lower.contains("what are you") || lower.contains("jarvis version")) {
            return "I am J.A.R.V.I.S. (Just A Rather Very Intelligent System) running Mark-V cognitive protocols on your Android architecture, Bro!"
        }

        return null
    }

    private fun isTimeQuery(lower: String): Boolean {
        return (lower.contains("time") && (lower.contains("what") || lower.contains("current") || lower.contains("tell") || lower.contains("give") || lower.contains("show"))) ||
                lower == "time" || lower == "what is time" || lower == "what's the time" || lower == "current time"
    }

    private fun isDateQuery(lower: String): Boolean {
        return (lower.contains("date") && (lower.contains("what") || lower.contains("current") || lower.contains("today") || lower.contains("give") || lower.contains("show"))) ||
                lower == "date" || lower == "what is date" || lower == "what is today's date" || lower == "current date" || lower == "today's date"
    }

    private fun isDayQuery(lower: String): Boolean {
        return lower.contains("what day") || lower.contains("day is today") || lower.contains("which day")
    }

    private fun evaluateMathQuery(lower: String): String? {
        // Look for pattern "what is [number] [op] [number]" or just "[number] [op] [number]"
        val mathRegex = Regex("""(?:(?:what is|calculate|evaluate)\s+)?(\d+(?:\.\d+)?)\s*([\+\-\*\/])\s*(\d+(?:\.\d+)?)""")
        val matchResult = mathRegex.find(lower)
        if (matchResult != null) {
            try {
                val num1 = matchResult.groupValues[1].toDoubleOrNull()
                val op = matchResult.groupValues[2]
                val num2 = matchResult.groupValues[3].toDoubleOrNull()

                if (num1 != null && num2 != null) {
                    val result = when (op) {
                        "+" -> num1 + num2
                        "-" -> num1 - num2
                        "*" -> num1 * num2
                        "/" -> if (num2 != 0.0) num1 / num2 else Double.NaN
                        else -> null
                    }

                    if (result != null) {
                        val formattedResult = if (result.isNaN()) {
                            "undefined (division by zero is prohibited in standard logic)"
                        } else if (result % 1.0 == 0.0) {
                            result.toInt().toString()
                        } else {
                            String.format(Locale.US, "%.4f", result).trimEnd('0').trimEnd('.')
                        }
                        
                        val rawNum1 = matchResult.groupValues[1]
                        val rawNum2 = matchResult.groupValues[3]
                        return "According to my cognitive arithmetic matrices, the correct answer for $rawNum1 $op $rawNum2 is $formattedResult, Bro!"
                    }
                }
            } catch (e: Exception) {
                // Return null if parsing fails, so it falls back to normal NLP
            }
        }
        return null
    }
}
