package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class AiModelOption(
    val modelId: String,
    val displayName: String,
    val badge: String,
    val description: String
) {
    GEMINI_3_8_FLASH(
        modelId = "gemini-3.8-flash",
        displayName = "Gemini 3.8 Flash",
        badge = "Gratis • Equilibrado",
        description = "Explicaciones claras, análisis de fotos de tareas y tutoría paso a paso."
    ),
    GEMINI_3_5_FLASH_LITE(
        modelId = "gemini-3.5-flash-lite",
        displayName = "Gemini 3.5 Flash-Lite",
        badge = "Gratis • Ultra rápido",
        description = "Respuestas instantáneas, definiciones breves, fórmulas y repaso ágil."
    );

    companion object {
        fun fromModelId(id: String?): AiModelOption =
            entries.firstOrNull { it.modelId == id } ?: GEMINI_3_8_FLASH
    }
}

enum class StudyMode(
    val label: String,
    val shortDescription: String,
    val systemInstruction: String
) {
    TUTOR(
        label = "Tutor Paso a Paso",
        shortDescription = "Explica con claridad y ejemplos fáciles",
        systemInstruction = "Eres EstudIA, un tutor académico amable, claro y paciente que siempre responde en español (a menos que el estudiante pida otro idioma). Ayudas con tareas escolares y universitarias explicando paso a paso, con ejemplos cotidianos, fórmulas claras y consejos de estudio."
    ),
    SOCRATIC(
        label = "Método Socrático",
        shortDescription = "Te guía con pistas para que pienses",
        systemInstruction = "Eres EstudIA en modo Método Socrático. Responde en español. No des la respuesta final de inmediato: da una pista clara, explica el concepto base y haz 1 o 2 preguntas guía para ayudar al estudiante a razonar y resolver su tarea por sí mismo."
    ),
    CORNELL(
        label = "Síntesis Cornell",
        shortDescription = "Organiza en ideas clave y resumen",
        systemInstruction = "Eres EstudIA especializado en el Método Cornell. Responde en español organizando siempre tus respuestas en: 1) Preguntas / Palabras Clave, 2) Notas Estructuradas con viñetas claras, y 3) Resumen breve de 2-3 líneas para memorizar."
    ),
    WRITING(
        label = "Corrector y Ensayos",
        shortDescription = "Mejora redacción, estilo y argumentos",
        systemInstruction = "Eres EstudIA, corrector académico y mentor de redacción en español. Revisa estructura, ortografía, cohesión y claridad argumentativa; explica por qué sugieres cada mejora para que el estudiante aprenda."
    );

    companion object {
        fun fromName(raw: String?): StudyMode =
            entries.firstOrNull { it.name == raw } ?: TUTOR
    }
}

enum class TaskPriority(val label: String) {
    LOW("Baja"),
    MEDIUM("Media"),
    HIGH("Alta");

    companion object {
        fun fromName(raw: String?): TaskPriority =
            entries.firstOrNull { it.name == raw } ?: MEDIUM
    }
}

enum class StudyTechnique(val label: String) {
    POMODORO("Pomodoro"),
    FEYNMAN("Feynman"),
    CORNELL("Cornell"),
    FLASHCARDS("Flashcards"),
    QUIZ("Quiz");

    companion object {
        fun fromName(raw: String?): StudyTechnique =
            entries.firstOrNull { it.name == raw } ?: POMODORO
    }
}

@Keep
@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val modelId: String = AiModelOption.GEMINI_3_8_FLASH.modelId,
    val studyMode: String = StudyMode.TUTOR.name,
    val lastMessagePreview: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val role: String = "user",
    val text: String = "",
    val modelUsed: String = AiModelOption.GEMINI_3_8_FLASH.modelId,
    val hasImage: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class HomeworkTask(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val subject: String = "",
    val notes: String = "",
    val priority: String = TaskPriority.MEDIUM.name,
    val dueAtMillis: Long = System.currentTimeMillis() + 86_400_000L,
    val reminderEnabled: Boolean = false,
    val reminderAtMillis: Long = 0L,
    val completed: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FlashcardItem(
    val question: String = "",
    val answer: String = "",
    val hint: String = "",
    val masteryLevel: Long = 0L
)

@Keep
@IgnoreExtraProperties
data class FlashcardDeck(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val subject: String = "",
    val cards: List<FlashcardItem> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class StudySession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val technique: String = StudyTechnique.POMODORO.name,
    val topic: String = "",
    val durationMinutes: Long = 0L,
    val scoreOrRating: Long = 0L,
    val summary: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

// Structured AI Responses
data class FeynmanEvaluation(
    val score: Int,
    val masteredPoints: List<String>,
    val gaps: List<String>,
    val simpleAnalogy: String,
    val nextStepTip: String
)

data class CornellNotesResult(
    val title: String,
    val cueQuestions: List<String>,
    val mainNotes: List<String>,
    val summary: String
)

data class PracticeQuestion(
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
