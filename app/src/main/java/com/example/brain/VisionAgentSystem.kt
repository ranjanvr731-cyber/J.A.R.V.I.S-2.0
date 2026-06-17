package com.example.brain

import android.util.Log

class VisionAgentSystem {
    private val TAG = "JarvisVisionAgent"

    data class VisionScene(
        val key: String,
        val displayName: String,
        val genericCategory: String, // "OBJECTS", "OCR", "DIAGRAM", "MATH_HW", "WHITEBOARD"
        val subject: String, // "Mathematics", "Computer Science", "Physics", "History", "General"
        val sceneDescription: String,
        val ocrExtractedText: String
    )

    private val availableScenes = listOf(
        VisionScene(
            key = "math_quadratic",
            displayName = "Algebra HW Paper (Quadratic math sheet)",
            genericCategory = "MATH_HW",
            subject = "Mathematics",
            sceneDescription = "A hand-written algebra worksheet containing quadratic equations with ink smudges.",
            ocrExtractedText = "Solve for x: 3x^2 - 5x + 2 = 0. Show all intermediate steps and the discriminant."
        ),
        VisionScene(
            key = "circuit_diagram",
            displayName = "Electronics Circuit drawing (Diagram)",
            genericCategory = "DIAGRAM",
            subject = "Physics / Engineering",
            sceneDescription = "A pen-and-paper circuit blueprint illustrating a series resistor network and an active haptic speaker.",
            ocrExtractedText = "Input: V_in = 12V. R1 = 150 Ohms. LED Forward Voltage drop = 2V. Calculate required limits."
        ),
        VisionScene(
            key = "computer_architecture_board",
            displayName = "IT Whiteboard (REST Api Architecture)",
            genericCategory = "WHITEBOARD",
            subject = "Computer Science",
            sceneDescription = "Classroom marker board diagram with directional flowcharts displaying server interactions.",
            ocrExtractedText = "Client -> Gateway (Authentication API) -> Worker Nodes (Kotlin threads) -> SQLite Database Cache."
        ),
        VisionScene(
            key = "history_textbook",
            displayName = "History Textbook page (Book reading snippet)",
            genericCategory = "OCR",
            subject = "History",
            sceneDescription = "Glossy page from a textbook containing historical illustrations and typed fonts.",
            ocrExtractedText = "Section 4.1: The Battle of Waterloo (1815). Napoleon Bonaparte clashed with Coalition forces under Wellington. Key outcome: Napoleon's definitive defeat and exile."
        ),
        VisionScene(
            key = "personal_desk",
            displayName = "Active Study Desk (Object scanner camera)",
            genericCategory = "OBJECTS",
            subject = "General / Space Analysis",
            sceneDescription = "A cluttered study desk viewed from a frontal perspective.",
            ocrExtractedText = "[OBJECTS IN MATRIX FIELD: Metal water bottle, opened Android notebook with code scribbles, phone with flashing LED, computer keyboard, leather chair.]"
        )
    )

    fun getScenes(): List<VisionScene> = availableScenes

    fun formVisionAnalysisPrompt(sceneKey: String, userExtraInput: String): String {
        val scene = availableScenes.find { it.key == sceneKey } ?: availableScenes[0]
        
        return buildString {
            append("You are JARVIS, analyzing a camera-scanned image coordinate.\n")
            append("[CAMERA SCAN DETAILS]\n")
            append("- Target Scene: ${scene.displayName}\n")
            append("- Classification: ${scene.genericCategory}\n")
            append("- Course Subject: ${scene.subject}\n")
            append("- Scene Properties: ${scene.sceneDescription}\n")
            append("- Extracted OCR Text: \"${scene.ocrExtractedText}\"\n")
            if (userExtraInput.isNotBlank()) {
                append("- User's direct verbal instructions: \"$userExtraInput\"\n")
            }
            append("\n[EXECUTIVE REPORT DIRECTIVES]\n")
            append("Perform the requested analysis with movie-realistic J.A.R.V.I.S science diagnostic flair. Use elegant Markdown and casual Tamil-English (Tanglish) headers or text where fitting, keeping it close, helpful, and friendly. ")
            
            when(scene.genericCategory) {
                "OBJECTS" -> {
                    append("Identify and outline all physical objects discovered. Note coordinates if helpful, estimate material properties, and explain what is visible in front of the camera scanning field, responding to 'en munnadi enna irukku?')")
                }
                "MATH_HW" -> {
                    append("Verify the subject to be Mathematics. Read the formula or query, solve step-by-step using rich scientific formatting (discriminant, square roots), explain intermediate reasoning, and explain the concept like an inspiring tutor.")
                }
                "DIAGRAM" -> {
                    append("Translate of the circuit components, diagrams, science schematics, or flowcharts. Explain current pathways, bottlenecks, and diagram flow details.")
                }
                "WHITEBOARD" -> {
                    append("Convert classroom notes, drawings, and formulas into highly-structured study guide notes with bullet points. Suggest optimization loops.")
                }
                "OCR" -> {
                    append("Summarize key textbook events, translate text, define complex terms inside the excerpt, and pose 2 quiz/follow-up questions to boost study retention.")
                }
            }
        }
    }
}
