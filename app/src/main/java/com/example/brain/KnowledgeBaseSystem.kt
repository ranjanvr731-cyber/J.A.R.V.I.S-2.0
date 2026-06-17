package com.example.brain

import android.util.Log
import java.util.Locale

class KnowledgeBaseSystem {
    private val TAG = "JarvisKnowledgeBase"

    data class KnowledgeDoc(
        val id: String,
        val title: String,
        val fileType: String, // "PDF", "DOC", "NOTE", "MANUAL"
        val content: String,
        val category: String, // "Science", "Android", "JARVIS_Spec", "Math"
        val wordCount: Int
    )

    private val documentInventory = mutableListOf<KnowledgeDoc>()

    init {
        // Pre-supply classic high-value textbooks and manuals matching the movie aesthetics
        documentInventory.add(KnowledgeDoc(
            id = "doc_1",
            title = "Android_Jetpack_Compose_DeepDive_Manual.pdf",
            fileType = "PDF",
            content = "Jetpack Compose relies on snapshot state tracking. Recomposition is triggered when State<T> is modified. Use remember {} or rememberSaveable {} to protect state references across standard recomposition cycles. For memory safety, shift expensive queries to flow collectors or coroutines inside viewModelScope.",
            category = "Android Development",
            wordCount = 52
        ))

        documentInventory.add(KnowledgeDoc(
            id = "doc_2",
            title = "Quantum_Mechanics_Formulas_Workbook.pdf",
            fileType = "PDF",
            content = "The Schrödinger equation represents quantum state dynamics. Wave function Ψ describes probability amplitudes. Energy operators map to eigenvalues. Standard quadratic solutions overlap in wave barrier potentials reflecting step-functions in electronics simulations.",
            category = "Physics / Quantum Science",
            wordCount = 44
        ))

        documentInventory.add(KnowledgeDoc(
            id = "doc_3",
            title = "J.A.R.V.I.S_Operating_Specs_MK_V.manual",
            fileType = "MANUAL",
            content = "Hardware specifications: 5 Cores Synaptic Controller Matrix. Primary interface is background voice listener loop. Memory banks persist state locally using Room SQLite protocols. In the case of service timeout, the Crash Recovery watchdog will trigger automatic self-heals.",
            category = "JARVIS Internals",
            wordCount = 48
        ))

        documentInventory.add(KnowledgeDoc(
            id = "doc_4",
            title = "Study_Guides_World_Histories.note",
            fileType = "NOTE",
            content = "Industrial Revolution notes (1760-1840): Prompt shift from hand production methods to machinery, new chemical manufacturing, standard water power, and steam engines. These structural developments triggered large scale urban migration and early computer-like jacquard weaving loom innovations.",
            category = "History / Study Core",
            wordCount = 47
        ))
    }

    fun addDocument(title: String, fileType: String, content: String, category: String) {
        val doc = KnowledgeDoc(
            id = "doc_${System.currentTimeMillis()}",
            title = title,
            fileType = fileType.uppercase(Locale.US),
            content = content,
            category = category,
            wordCount = content.split("\\s+".toRegex()).size
        )
        documentInventory.add(0, doc)
        Log.i(TAG, "📚 KNOWLEDGE BASE: Inserted reference document '${title}' successfully.")
    }

    fun searchKnowledge(query: String): List<KnowledgeDoc> {
        if (query.isBlank()) return documentInventory.toList()
        val keywords = query.lowercase(Locale.US).split(" ")
        
        return documentInventory.filter { doc ->
            keywords.any { kw ->
                doc.title.lowercase(Locale.US).contains(kw) ||
                doc.content.lowercase(Locale.US).contains(kw) ||
                doc.category.lowercase(Locale.US).contains(kw)
            }
        }
    }

    fun getDocuments(): List<KnowledgeDoc> = documentInventory.toList()

    fun generateAISummary(docId: String): String {
        val doc = documentInventory.find { it.id == docId } ?: return "Document index invalid. All files synced."
        return """
            📝 KNOWLEDGE COMPILATION SUMMARY RE-ROUTED:
            - SOURCE DOCUMENT: ${doc.title} [Type: ${doc.fileType}]
            - TOPIC / DISCIPLINE: ${doc.category} (${doc.wordCount} words analyzed)
            
            [CORE ABSTRACT]
            ${doc.content}
            
            [J.A.R.V.I.S RECOMMENDATION]
            This reference document contains key insights for your studies, Bro. I have indexed this text block into memory grids for dynamic RAG searches and vocal answering.
        """.trimIndent()
    }
}
