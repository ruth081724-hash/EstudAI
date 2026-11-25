package com.example.data

import android.content.Context
import com.example.R
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.example.model.AiModelOption
import com.example.model.ChatMessage
import com.example.model.Conversation
import com.example.model.FlashcardDeck
import com.example.model.FlashcardItem
import com.example.model.HomeworkTask
import com.example.model.StudyMode
import com.example.model.StudySession
import com.example.model.StudyTechnique
import com.example.model.TaskPriority
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FirestoreRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) : StudyDataRepository {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(expectedUserId: String? = null): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("Debes iniciar sesión con Google para guardar tus datos.")
        if (expectedUserId != null) {
            check(uid == expectedUserId) { "La sesión activa cambió; operación cancelada." }
        }
        return uid
    }

    private fun <T> observeCollection(
        uid: String,
        path: String,
        query: Query,
        mapper: (List<DocumentSnapshot>) -> List<T>
    ): Flow<List<T>> = flow {
        emitAll(
            query.snapshots()
                .map { snapshot -> mapper(snapshot.documents) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    // --- Conversations & Messages ---

    override fun observeConversations(userId: String): Flow<List<Conversation>> {
        val path = "users/$userId/conversations"
        val query = db.collection("users").document(userId).collection("conversations")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
        return observeCollection(userId, path, query) { docs ->
            docs.mapNotNull {
                it.toObject(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
        }
    }

    override fun observeMessages(userId: String, conversationId: String): Flow<List<ChatMessage>> {
        val path = "users/$userId/conversations/$conversationId/messages"
        val query = db.collection("users").document(userId)
            .collection("conversations").document(conversationId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
        return observeCollection(userId, path, query) { docs ->
            docs.mapNotNull {
                it.toObject(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
        }
    }

    override suspend fun createConversation(
        userId: String,
        title: String,
        modelOption: AiModelOption,
        studyMode: StudyMode,
        lastMessagePreview: String
    ): Result<String> {
        var docPath = "users/$userId/conversations"
        return try {
            val uid = requireUserId(userId)
            val cleanTitle = title.trim().ifEmpty { "Nueva sesión de estudio" }.take(120)
            val cleanPreview = lastMessagePreview.trim().take(300)
            val docRef = db.collection("users").document(uid)
                .collection("conversations").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "title" to cleanTitle,
                "modelId" to modelOption.modelId,
                "studyMode" to studyMode.name,
                "lastMessagePreview" to cleanPreview,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun updateConversationMeta(
        userId: String,
        conversationId: String,
        title: String,
        modelOption: AiModelOption,
        studyMode: StudyMode,
        lastMessagePreview: String
    ): Result<Unit> {
        val docPath = "users/$userId/conversations/$conversationId"
        return try {
            val uid = requireUserId(userId)
            require(conversationId.isNotBlank()) { "conversationId requerido" }
            val cleanTitle = title.trim().ifEmpty { "Sesión de estudio" }.take(120)
            val cleanPreview = lastMessagePreview.trim().take(300)
            db.collection("users").document(uid)
                .collection("conversations").document(conversationId)
                .update(
                    mapOf(
                        "title" to cleanTitle,
                        "modelId" to modelOption.modelId,
                        "studyMode" to studyMode.name,
                        "lastMessagePreview" to cleanPreview,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun deleteConversation(userId: String, conversationId: String): Result<Unit> {
        val docPath = "users/$userId/conversations/$conversationId"
        return try {
            val uid = requireUserId(userId)
            require(conversationId.isNotBlank()) { "conversationId requerido" }
            val convRef = db.collection("users").document(uid)
                .collection("conversations").document(conversationId)
            val msgSnapshots = convRef.collection("messages").get().await()
            for (doc in msgSnapshots.documents) {
                doc.reference.delete().await()
            }
            convRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun addMessage(
        userId: String,
        conversationId: String,
        role: String,
        text: String,
        modelUsed: String,
        hasImage: Boolean
    ): Result<Unit> {
        var docPath = "users/$userId/conversations/$conversationId/messages"
        return try {
            val uid = requireUserId(userId)
            require(conversationId.isNotBlank()) { "conversationId requerido" }
            require(role == "user" || role == "model") { "role inválido" }
            val cleanText = text.trim().ifEmpty { "..." }.take(20000)
            val docRef = db.collection("users").document(uid)
                .collection("conversations").document(conversationId)
                .collection("messages").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "role" to role,
                "text" to cleanText,
                "modelUsed" to modelUsed.take(64),
                "hasImage" to hasImage,
                "createdAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    // --- Homework Tasks & Reminders ---

    override fun observeHomeworkTasks(userId: String): Flow<List<HomeworkTask>> {
        val path = "users/$userId/homeworkTasks"
        val query = db.collection("users").document(userId).collection("homeworkTasks")
            .orderBy("dueAtMillis", Query.Direction.ASCENDING)
        return observeCollection(userId, path, query) { docs ->
            docs.mapNotNull {
                it.toObject(HomeworkTask::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
        }
    }

    override suspend fun addHomeworkTask(
        userId: String,
        title: String,
        subject: String,
        notes: String,
        priority: TaskPriority,
        dueAtMillis: Long,
        reminderEnabled: Boolean,
        reminderAtMillis: Long
    ): Result<String> {
        var docPath = "users/$userId/homeworkTasks"
        return try {
            val uid = requireUserId(userId)
            val cleanTitle = title.trim().take(150)
            val cleanSubject = subject.trim().ifEmpty { "General" }.take(60)
            val cleanNotes = notes.trim().take(1000)
            require(cleanTitle.isNotEmpty()) { "El título de la tarea no puede estar vacío." }
            require(dueAtMillis > 0L) { "La fecha de entrega debe ser válida." }

            val docRef = db.collection("users").document(uid)
                .collection("homeworkTasks").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "title" to cleanTitle,
                "subject" to cleanSubject,
                "notes" to cleanNotes,
                "priority" to priority.name,
                "dueAtMillis" to dueAtMillis,
                "reminderEnabled" to reminderEnabled,
                "reminderAtMillis" to reminderAtMillis.coerceAtLeast(0L),
                "completed" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun updateHomeworkTask(
        userId: String,
        taskId: String,
        title: String,
        subject: String,
        notes: String,
        priority: TaskPriority,
        dueAtMillis: Long,
        reminderEnabled: Boolean,
        reminderAtMillis: Long,
        completed: Boolean
    ): Result<Unit> {
        val docPath = "users/$userId/homeworkTasks/$taskId"
        return try {
            val uid = requireUserId(userId)
            require(taskId.isNotBlank()) { "taskId requerido" }
            val cleanTitle = title.trim().take(150)
            val cleanSubject = subject.trim().ifEmpty { "General" }.take(60)
            val cleanNotes = notes.trim().take(1000)
            require(cleanTitle.isNotEmpty()) { "El título de la tarea no puede estar vacío." }

            db.collection("users").document(uid)
                .collection("homeworkTasks").document(taskId)
                .update(
                    mapOf(
                        "title" to cleanTitle,
                        "subject" to cleanSubject,
                        "notes" to cleanNotes,
                        "priority" to priority.name,
                        "dueAtMillis" to dueAtMillis.coerceAtLeast(1L),
                        "reminderEnabled" to reminderEnabled,
                        "reminderAtMillis" to reminderAtMillis.coerceAtLeast(0L),
                        "completed" to completed,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun toggleHomeworkCompleted(
        userId: String,
        taskId: String,
        completed: Boolean
    ): Result<Unit> {
        val docPath = "users/$userId/homeworkTasks/$taskId"
        return try {
            val uid = requireUserId(userId)
            require(taskId.isNotBlank()) { "taskId requerido" }
            db.collection("users").document(uid)
                .collection("homeworkTasks").document(taskId)
                .update(
                    mapOf(
                        "completed" to completed,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun deleteHomeworkTask(userId: String, taskId: String): Result<Unit> {
        val docPath = "users/$userId/homeworkTasks/$taskId"
        return try {
            val uid = requireUserId(userId)
            require(taskId.isNotBlank()) { "taskId requerido" }
            db.collection("users").document(uid)
                .collection("homeworkTasks").document(taskId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- Flashcard Decks & Study Sessions ---

    override fun observeFlashcardDecks(userId: String): Flow<List<FlashcardDeck>> {
        val path = "users/$userId/flashcardDecks"
        val query = db.collection("users").document(userId).collection("flashcardDecks")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
        return observeCollection(userId, path, query) { docs ->
            docs.mapNotNull { doc ->
                val obj = doc.toObject(FlashcardDeck::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                obj
            }
        }
    }

    override suspend fun saveFlashcardDeck(
        userId: String,
        title: String,
        subject: String,
        cards: List<FlashcardItem>
    ): Result<String> {
        var docPath = "users/$userId/flashcardDecks"
        return try {
            val uid = requireUserId(userId)
            val cleanTitle = title.trim().ifEmpty { "Mazo de estudio" }.take(120)
            val cleanSubject = subject.trim().ifEmpty { "General" }.take(60)
            val boundedCards = cards.take(30).map { card ->
                mapOf(
                    "question" to card.question.trim().take(500),
                    "answer" to card.answer.trim().take(1000),
                    "hint" to card.hint.trim().take(300),
                    "masteryLevel" to card.masteryLevel.coerceIn(0L, 5L)
                )
            }
            require(boundedCards.isNotEmpty()) { "El mazo debe tener al menos una tarjeta." }

            val docRef = db.collection("users").document(uid)
                .collection("flashcardDecks").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "title" to cleanTitle,
                "subject" to cleanSubject,
                "cards" to boundedCards,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun updateFlashcardDeckCards(
        userId: String,
        deckId: String,
        cards: List<FlashcardItem>
    ): Result<Unit> {
        val docPath = "users/$userId/flashcardDecks/$deckId"
        return try {
            val uid = requireUserId(userId)
            require(deckId.isNotBlank()) { "deckId requerido" }
            val boundedCards = cards.take(30).map { card ->
                mapOf(
                    "question" to card.question.trim().take(500),
                    "answer" to card.answer.trim().take(1000),
                    "hint" to card.hint.trim().take(300),
                    "masteryLevel" to card.masteryLevel.coerceIn(0L, 5L)
                )
            }
            require(boundedCards.isNotEmpty()) { "El mazo debe tener al menos una tarjeta." }
            db.collection("users").document(uid)
                .collection("flashcardDecks").document(deckId)
                .update(
                    mapOf(
                        "cards" to boundedCards,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun deleteFlashcardDeck(userId: String, deckId: String): Result<Unit> {
        val docPath = "users/$userId/flashcardDecks/$deckId"
        return try {
            val uid = requireUserId(userId)
            require(deckId.isNotBlank()) { "deckId requerido" }
            db.collection("users").document(uid)
                .collection("flashcardDecks").document(deckId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    override fun observeStudySessions(userId: String): Flow<List<StudySession>> {
        val path = "users/$userId/studySessions"
        val query = db.collection("users").document(userId).collection("studySessions")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(30)
        return observeCollection(userId, path, query) { docs ->
            docs.mapNotNull {
                it.toObject(StudySession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
        }
    }

    override suspend fun logStudySession(
        userId: String,
        technique: StudyTechnique,
        topic: String,
        durationMinutes: Long,
        scoreOrRating: Long,
        summary: String
    ): Result<Unit> {
        var docPath = "users/$userId/studySessions"
        return try {
            val uid = requireUserId(userId)
            val cleanTopic = topic.trim().ifEmpty { technique.label }.take(150)
            val docRef = db.collection("users").document(uid)
                .collection("studySessions").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "technique" to technique.name,
                "topic" to cleanTopic,
                "durationMinutes" to durationMinutes.coerceIn(0L, 240L),
                "scoreOrRating" to scoreOrRating.coerceIn(0L, 100L),
                "summary" to summary.trim().take(2000),
                "createdAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }
}
