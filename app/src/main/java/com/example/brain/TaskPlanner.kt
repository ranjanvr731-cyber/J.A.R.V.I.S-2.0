package com.example.brain

import android.util.Log

class TaskPlanner {
    private val TAG = "JarvisTaskPlanner"
    private val tasks = mutableListOf<ExecutionTask>()

    data class ExecutionTask(
        val taskId: String,
        val description: String,
        var isCompleted: Boolean = false,
        var status: String = "Pending"
    )

    // Break down complex inputs into modular steps
    fun createExecutionPlan(complexInput: String): List<ExecutionTask> {
        tasks.clear()
        val lower = complexInput.lowercase()

        when {
            lower.contains("stealth mode") || lower.contains("safety guard") -> {
                tasks.add(ExecutionTask("1", "Check active hardware sensors", false))
                tasks.add(ExecutionTask("2", "Secure sensitive permissions status", false))
                tasks.add(ExecutionTask("3", "Deactivate flashlight spotlight and trigger vibrate pulse", false))
                tasks.add(ExecutionTask("4", "Turn on Do Not Disturb (DND) mode", false))
            }
            lower.contains("morning routine") || lower.contains("wake up") -> {
                tasks.add(ExecutionTask("1", "Run self-diagnostic check on channels", false))
                tasks.add(ExecutionTask("2", "Vibrate device briefly to alert user", false))
                tasks.add(ExecutionTask("3", "Summarize pending missed notifications", false))
                tasks.add(ExecutionTask("4", "Greet user warmly using friendly Tanglish sentiment", false))
            }
            else -> {
                tasks.add(ExecutionTask("1", "Analyze natural language expression query", false))
                tasks.add(ExecutionTask("2", "Identify automation macros or online APIs", false))
                tasks.add(ExecutionTask("3", "Formulate snapping response payload", false))
            }
        }
        
        Log.i(TAG, "Formulated active planning steps count: ${tasks.size}")
        return tasks.toList()
    }

    // Complete a step
    fun updateTaskProgress(taskId: String, success: Boolean) {
        val task = tasks.find { it.taskId == taskId }
        if (task != null) {
            task.isCompleted = success
            task.status = if (success) "Completed" else "Failed"
            Log.d(TAG, "Planning Step $taskId Updated: ${task.status}")
        }
    }

    fun getActivePlan(): List<ExecutionTask> = tasks.toList()
}
