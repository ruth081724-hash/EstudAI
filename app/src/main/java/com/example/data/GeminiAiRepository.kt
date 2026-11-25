package com.example.data

import android.graphics.Bitmap
import android.util.Log
import com.example.model.AiModelOption
import com.example.model.ChatMessage
import com.example.model.CornellNotesResult
import com.example.model.FeynmanEvaluation
import com.example.model.FlashcardItem
import com.example.model.PracticeQuestion
import com.example.model.StudyMode
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "GeminiAiRepository"
private const val AI_TIMEOUT_MS = 60_000L

class GeminiAiRepository {

    private val chatModels = ConcurrentHashMap<Pair<AiModelOption, StudyMode>, GenerativeModel>()

    private fun getChatModel(modelOption: AiModelOption, studyMode: StudyMode): GenerativeModel {
        return chatModels.getOrPut(modelOption to studyMode) {
            Firebase.ai.generativeModel(
                modelName = modelOption.modelId,
                systemInstruction = content {
                    text(studyMode.systemInstruction)
                }
            )
        }
    }

    private fun scaleBitmapForUpload(bitmap: Bitmap): Bitmap {
        val maxDim = 1280
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDim && height <= maxDim) return bitmap
        val ratio = minOf(maxDim.toFloat() / width, maxDim.toFloat() / height)
        val newW = (width * ratio).toInt().coerceAtLeast(1)
        val newH = (height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }

    fun streamChatReply(
        modelOption: AiModelOption,
        studyMode: StudyMode,
        history: List<ChatMessage>,
        userPrompt: String,
        attachedBitmap: Bitmap? = null
    ): Flow<String> = flow {
        val model = getChatModel(modelOption, studyMode)
        val chatHistory = history.takeLast(14).map { msg ->
            content(role = if (msg.role == "model") "model" else "user") {
                text(msg.text)
            }
        }
        val chat = model.startChat(history = chatHistory)
        val safeBitmap = attachedBitmap?.let { scaleBitmapForUpload(it) }
        val userContent = content {
            if (safeBitmap != null) {
                image(safeBitmap)
            }
            text(userPrompt)
        }

        val accumulated = StringBuilder()
        try {
            withTimeout(AI_TIMEOUT_MS) {
                chat.sendMessageStream(userContent).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        emit(accumulated.toString())
                    }
                }
            }
            if (accumulated.isBlank()) {
                throw IllegalStateException("El modelo devolvió una respuesta vacía.")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Chat stream timed out", e)
            throw IllegalStateException("La respuesta tardó más de 60 segundos. Inténtalo de nuevo.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Chat stream failed", e)
            throw e
        }
    }

    suspend fun evaluateFeynmanExplanation(
        modelOption: AiModelOption,
        topic: String,
        userExplanation: String
    ): Result<FeynmanEvaluation> {
        val schema = Schema.obj(
            properties = mapOf(
                "score" to Schema.integer(description = "Puntaje de comprensión de 0 a 100"),
                "masteredPoints" to Schema.array(
                    items = Schema.string(),
                    description = "Ideas correctas que el estudiante explicó bien (máximo 4)"
                ),
                "gaps" to Schema.array(
                    items = Schema.string(),
                    description = "Lagunas, imprecisiones o conceptos por reforzar (máximo 4)"
                ),
                "simpleAnalogy" to Schema.string(description = "Analogía sencilla en español para recordar el tema"),
                "nextStepTip" to Schema.string(description = "Consejo concreto para mejorar la siguiente explicación")
            )
        )
        val config = generationConfig {
            responseMimeType = "application/json"
            responseSchema = schema
        }
        val model = Firebase.ai.generativeModel(
            modelName = modelOption.modelId,
            generationConfig = config
        )

        return try {
            val response = withTimeout(AI_TIMEOUT_MS) {
                model.generateContent(
                    """
                    Evalúa en español esta explicación usando la Técnica Feynman.
                    Tema: "$topic"
                    Explicación del estudiante: "$userExplanation"
                    Sé alentador, específico y pedagógico.
                    """.trimIndent()
                )
            }
            val raw = response.text ?: throw IllegalStateException("Respuesta vacía del modelo.")
            val json = JSONObject(raw)
            val masteredArr = json.optJSONArray("masteredPoints") ?: JSONArray()
            val gapsArr = json.optJSONArray("gaps") ?: JSONArray()
            Result.success(
                FeynmanEvaluation(
                    score = json.optInt("score", 70).coerceIn(0, 100),
                    masteredPoints = (0 until masteredArr.length()).mapNotNull {
                        masteredArr.optString(it).takeIf { s -> s.isNotBlank() }
                    },
                    gaps = (0 until gapsArr.length()).mapNotNull {
                        gapsArr.optString(it).takeIf { s -> s.isNotBlank() }
                    },
                    simpleAnalogy = json.optString("simpleAnalogy", ""),
                    nextStepTip = json.optString("nextStepTip", "")
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Feynman eval timeout", e)
            Result.failure(IllegalStateException("Tiempo agotado al evaluar tu explicación."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Feynman eval failed", e)
            Result.failure(e)
        }
    }

    suspend fun generateFlashcardDeck(
        modelOption: AiModelOption,
        topic: String,
        count: Int = 6
    ): Result<Pair<String, List<FlashcardItem>>> {
        val cardSchema = Schema.obj(
            properties = mapOf(
                "question" to Schema.string(description = "Pregunta clara de repaso activo"),
                "answer" to Schema.string(description = "Respuesta precisa y fácil de memorizar"),
                "hint" to Schema.string(description = "Pista breve sin revelar la respuesta")
            )
        )
        val deckSchema = Schema.obj(
            properties = mapOf(
                "deckTitle" to Schema.string(description = "Título corto del mazo en español"),
                "cards" to Schema.array(
                    items = cardSchema,
                    description = "Lista de tarjetas de estudio"
                )
            )
        )
        val config = generationConfig {
            responseMimeType = "application/json"
            responseSchema = deckSchema
        }
        val model = Firebase.ai.generativeModel(
            modelName = modelOption.modelId,
            generationConfig = config
        )

        return try {
            val safeCount = count.coerceIn(3, 12)
            val response = withTimeout(AI_TIMEOUT_MS) {
                model.generateContent(
                    "Crea exactamente $safeCount flashcards en español para Active Recall sobre: $topic. Preguntas desafiantes pero claras."
                )
            }
            val raw = response.text ?: throw IllegalStateException("Respuesta vacía del modelo.")
            val json = JSONObject(raw)
            val title = json.optString("deckTitle", topic).ifBlank { topic }.take(120)
            val arr = json.optJSONArray("cards") ?: JSONArray()
            val items = (0 until arr.length()).mapNotNull { idx ->
                val obj = arr.optJSONObject(idx) ?: return@mapNotNull null
                FlashcardItem(
                    question = obj.optString("question", ""),
                    answer = obj.optString("answer", ""),
                    hint = obj.optString("hint", ""),
                    masteryLevel = 0L
                )
            }.filter { it.question.isNotBlank() && it.answer.isNotBlank() }
            if (items.isEmpty()) throw IllegalStateException("No se pudieron generar tarjetas.")
            Result.success(title to items)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Flashcard generation timeout", e)
            Result.failure(IllegalStateException("Tiempo agotado al generar flashcards."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Flashcard generation failed", e)
            Result.failure(e)
        }
    }

    suspend fun generateCornellNotes(
        modelOption: AiModelOption,
        topic: String,
        rawNotes: String
    ): Result<CornellNotesResult> {
        val schema = Schema.obj(
            properties = mapOf(
                "title" to Schema.string(description = "Título académico del apunte"),
                "cueQuestions" to Schema.array(
                    items = Schema.string(),
                    description = "4 a 6 preguntas clave o conceptos detonantes para la columna izquierda"
                ),
                "mainNotes" to Schema.array(
                    items = Schema.string(),
                    description = "4 a 6 ideas desarrolladas con claridad para la columna derecha"
                ),
                "summary" to Schema.string(description = "Resumen sintetizado en 2 o 3 oraciones")
            )
        )
        val config = generationConfig {
            responseMimeType = "application/json"
            responseSchema = schema
        }
        val model = Firebase.ai.generativeModel(
            modelName = modelOption.modelId,
            generationConfig = config
        )

        return try {
            val promptBody = if (rawNotes.isBlank()) {
                "Genera apuntes en español usando el Método Cornell sobre el tema: $topic."
            } else {
                "Convierte y organiza estas notas en español usando el Método Cornell.\nTema: $topic\nNotas base:\n$rawNotes"
            }
            val response = withTimeout(AI_TIMEOUT_MS) {
                model.generateContent(promptBody)
            }
            val raw = response.text ?: throw IllegalStateException("Respuesta vacía del modelo.")
            val json = JSONObject(raw)
            val cues = json.optJSONArray("cueQuestions") ?: JSONArray()
            val notes = json.optJSONArray("mainNotes") ?: JSONArray()
            Result.success(
                CornellNotesResult(
                    title = json.optString("title", topic).ifBlank { topic },
                    cueQuestions = (0 until cues.length()).mapNotNull {
                        cues.optString(it).takeIf { s -> s.isNotBlank() }
                    },
                    mainNotes = (0 until notes.length()).mapNotNull {
                        notes.optString(it).takeIf { s -> s.isNotBlank() }
                    },
                    summary = json.optString("summary", "")
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Cornell timeout", e)
            Result.failure(IllegalStateException("Tiempo agotado al generar apuntes Cornell."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Cornell failed", e)
            Result.failure(e)
        }
    }

    suspend fun generatePracticeQuiz(
        modelOption: AiModelOption,
        topic: String
    ): Result<List<PracticeQuestion>> {
        val questionSchema = Schema.obj(
            properties = mapOf(
                "question" to Schema.string(description = "Pregunta de opción múltiple en español"),
                "options" to Schema.array(
                    items = Schema.string(),
                    description = "Exactamente 4 opciones de respuesta"
                ),
                "correctIndex" to Schema.integer(description = "Índice de la opción correcta (0 a 3)"),
                "explanation" to Schema.string(description = "Breve explicación de por qué es correcta")
            )
        )
        val quizSchema = Schema.obj(
            properties = mapOf(
                "questions" to Schema.array(
                    items = questionSchema,
                    description = "Lista de 5 preguntas de práctica"
                )
            )
        )
        val config = generationConfig {
            responseMimeType = "application/json"
            responseSchema = quizSchema
        }
        val model = Firebase.ai.generativeModel(
            modelName = modelOption.modelId,
            generationConfig = config
        )

        return try {
            val response = withTimeout(AI_TIMEOUT_MS) {
                model.generateContent(
                    "Crea un mini quiz de práctica de 5 preguntas en español con 4 opciones cada una sobre: $topic."
                )
            }
            val raw = response.text ?: throw IllegalStateException("Respuesta vacía del modelo.")
            val json = JSONObject(raw)
            val arr = json.optJSONArray("questions") ?: JSONArray()
            val list = (0 until arr.length()).mapNotNull { idx ->
                val q = arr.optJSONObject(idx) ?: return@mapNotNull null
                val optsArr = q.optJSONArray("options") ?: JSONArray()
                val opts = (0 until optsArr.length()).mapNotNull {
                    optsArr.optString(it).takeIf { s -> s.isNotBlank() }
                }
                PracticeQuestion(
                    question = q.optString("question", ""),
                    options = opts,
                    correctIndex = q.optInt("correctIndex", 0).coerceIn(0, (opts.size - 1).coerceAtLeast(0)),
                    explanation = q.optString("explanation", "")
                )
            }.filter { it.question.isNotBlank() && it.options.size >= 2 }
            if (list.isEmpty()) throw IllegalStateException("No se pudieron generar preguntas.")
            Result.success(list)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Quiz timeout", e)
            Result.failure(IllegalStateException("Tiempo agotado al generar el quiz."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Quiz failed", e)
            Result.failure(e)
        }
    }

    suspend fun suggestHomeworkStudyPlan(
        modelOption: AiModelOption,
        taskTitle: String,
        subject: String,
        notes: String
    ): Result<String> {
        val model = Firebase.ai.generativeModel(
            modelName = modelOption.modelId,
            systemInstruction = content {
                text("Eres EstudIA, planificador académico. Crea planes cortos, accionables y realistas en español usando bloques Pomodoro y metodologías de estudio activas.")
            }
        )
        return try {
            val response = withTimeout(AI_TIMEOUT_MS) {
                model.generateContent(
                    """
                    Crea un plan de estudio concreto para esta tarea:
                    - Materia: $subject
                    - Tarea: $taskTitle
                    - Detalles: ${notes.ifBlank { "Sin notas adicionales" }}
                    Incluye: 1) Cómo dividirla en pasos, 2) Técnica recomendada (Pomodoro, Feynman, Cornell o Active Recall), 3) Primer paso de 10 minutos para empezar ya.
                    """.trimIndent()
                )
            }
            val text = response.text?.trim().orEmpty()
            if (text.isEmpty()) throw IllegalStateException("El modelo devolvió una respuesta vacía.")
            Result.success(text)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Study plan timeout", e)
            Result.failure(IllegalStateException("Tiempo agotado al crear el plan de tarea."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Study plan failed", e)
            Result.failure(e)
        }
    }
}
