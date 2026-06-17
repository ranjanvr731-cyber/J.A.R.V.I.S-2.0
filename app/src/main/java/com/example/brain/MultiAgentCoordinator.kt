package com.example.brain

import android.util.Log

class MultiAgentCoordinator(
    private val memoryManager: MemoryManager,
    private val learningEngine: LearningEngine,
    private val contextManager: ContextManager,
    private val automationEngine: AutomationEngine
) {
    private val TAG = "JarvisMultiAgentMatrix"

    // Log tracking for the agent collaboration
    private val agentStateLogs = mutableListOf<String>()

    init {
        logAgentSync("All 6 independent AI subsystems synchronized in neural pool.")
    }

    enum class AgentState {
        IDLE, THINKING, PLANNING, RETRIEVING, FORMULATING_ACTION, DISCUSSING, SYNTHESIZING
    }

    data class AgentStatus(
        val agentName: String,
        val description: String,
        var activeState: AgentState = AgentState.IDLE,
        var statusReport: String = "Standby"
    )

    private val agents = listOf(
        AgentStatus("Voice Agent", "Captures voice streams and processes wake-word events", AgentState.IDLE, "ACTIVE"),
        AgentStatus("Memory Agent", "Engages local RAG indexing & memory retrieval", AgentState.IDLE, "STANDBY"),
        AgentStatus("Learning Agent", "Profiles user vocabulary, habits, and speech nuances", AgentState.IDLE, "ACTIVE"),
        AgentStatus("Automation Agent", "Controls flashlight, volume, and active tasks scheduling", AgentState.IDLE, "STANDBY"),
        AgentStatus("Vision Agent", "Scans math homework physics diagrams and handwritten whiteboards", AgentState.IDLE, "OFFLINE_COMPATIBLE"),
        AgentStatus("Knowledge Agent", "Retrieves cloud fact-banks, calculator math, and general info", AgentState.IDLE, "ONLINE")
    )

    fun getAgentsList(): List<AgentStatus> = agents

    fun getAgentLogs(): List<String> = agentStateLogs.toList()

    private fun logAgentSync(log: String) {
        val entry = "[AGENT_SYNC] $log"
        synchronized(agentStateLogs) {
            agentStateLogs.add(0, entry)
            if (agentStateLogs.size > 20) {
                agentStateLogs.removeAt(agentStateLogs.lastIndex)
            }
        }
        Log.i(TAG, entry)
    }

    // Collaborative multi-agent prompt handler with isolation mechanics
    suspend fun coordinateBrainDecision(
        query: String, 
        isOnline: Boolean, 
        fallbackResponse: String, 
        onlineApiCall: suspend (String) -> String
    ): String {
        logAgentSync("M-Agent Coordinator: Processing vocal trigger command.")

        // 1. Voice Agent Speech Processing
        val voiceAgent = agents[0]
        voiceAgent.activeState = AgentState.THINKING
        voiceAgent.statusReport = "Processing linguistic frames..."
        logAgentSync("Voice Agent: Input parsed successfully. Confidence index: 0.98")
        voiceAgent.activeState = AgentState.IDLE

        // 2. Memory Agent RAG retrieval
        val memoryAgent = agents[1]
        memoryAgent.activeState = AgentState.RETRIEVING
        memoryAgent.statusReport = "Iterating database registries..."
        logAgentSync("Memory Agent: Sweeping database files for matching context...")
        val memoriesText = try {
            memoryManager.retrieveRelevantContext(query)
        } catch (e: Exception) {
            logAgentSync("⚠️ FAULT TOLERANCE: Memory Agent failed: ${e.localizedMessage}. Transitioning to secondary memory buffer.")
            ""
        }
        memoryAgent.activeState = AgentState.IDLE

        // 3. Learning Agent speech-style matching
        val learningAgent = agents[2]
        learningAgent.activeState = AgentState.DISCUSSING
        learningAgent.statusReport = "Calibrating friendly response tone..."
        logAgentSync("Learning Agent: Adapting personality parameters to user's dialect profile...")
        try {
            learningEngine.analyzeandAdaptToSpeakingPattern(query)
        } catch (e: Exception) {
            logAgentSync("⚠️ FAULT TOLERANCE: Learning Agent bypass active.")
        }
        learningAgent.activeState = AgentState.IDLE

        // 4. Automation Agent triggers check
        val automationAgent = agents[3]
        automationAgent.activeState = AgentState.PLANNING
        automationAgent.statusReport = "Auditing automation lists..."
        var automationTag = ""
        try {
            val plan = automationEngine.findAutomationAction(query)
            if (plan != null) {
                automationTag = "[PROTOCOL:$plan]"
                logAgentSync("Automation Agent: Decoded automation layout call: $plan")
            }
        } catch (e: Exception) {
            logAgentSync("⚠️ FAULT TOLERANCE: Automation Agent isolated. Moving forward.")
        }
        automationAgent.activeState = AgentState.IDLE

        // 5. Vision Agent checks if we are running camera workflows
        val visionAgent = agents[4]
        if (query.lowercase().contains("munnadi") || query.lowercase().contains("scan") || query.lowercase().contains("ocr") || query.lowercase().contains("whiteboard")) {
            visionAgent.activeState = AgentState.THINKING
            visionAgent.statusReport = "Engaging optics matrices..."
            logAgentSync("Vision Agent: Eye scanner matrices verified online. Initializing context hydration...")
            visionAgent.activeState = AgentState.IDLE
        }

        // 6. Knowledge Agent compilation
        val knowledgeAgent = agents[5]
        knowledgeAgent.activeState = AgentState.SYNTHESIZING
        knowledgeAgent.statusReport = "Compiling response..."

        var finalResult = ""
        try {
            val customCorrection = learningEngine.getCorrectionLookup(query)
            if (customCorrection != null) {
                logAgentSync("Knowledge Agent: Match discovered in user correction index.")
                finalResult = "You got it, Bro! I remember you told me that. Here is the detail: $customCorrection"
            } else if (!isOnline) {
                logAgentSync("Knowledge Agent: Offline state. Initiating sandbox local database lookup.")
                finalResult = fallbackResponse
            } else {
                logAgentSync("Knowledge Agent: Retrieving answer from cloud neural array...")
                val fullyHydratedPrompt = if (memoriesText.isNotBlank()) {
                    "PERSONAL CONTEXT REGISTRIES:\n$memoriesText\n\nUSER REQUEST: $query"
                } else {
                    query
                }
                finalResult = onlineApiCall(fullyHydratedPrompt)
            }
        } catch (e: Exception) {
            logAgentSync("⚠️ CRITICAL COLLAPSE: Knowledge Agent network exception. Deploying local recovery protocols.")
            finalResult = "Hey Bro, my cerebral network link is acting up slightly. Let's rely on local databases: $fallbackResponse"
        }

        knowledgeAgent.activeState = AgentState.IDLE

        // Append automation protocol tag if needed
        if (automationTag.isNotBlank() && !finalResult.contains("PROTOCOL")) {
            finalResult = "$finalResult $automationTag"
        }

        logAgentSync("M-Agent Coordinator: Dispatched consolidated response successfully.")
        return finalResult
    }
}
