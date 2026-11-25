package com.example.data

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
import kotlinx.coroutines.flow.Flow

interface StudyDataRepository {
    fun observeConversations(userId: String): Flow<List<Conversation>>
    fun observeMessages(userId: String, conversationId: String): Flow<List<ChatMessage>>
    suspend fun createConversation(
        userId: String,
        title: String,
        modelOption: AiModelOption,
        studyMode: StudyMode,
        lastMessagePreview: String = ""
    ): Result<String>

    suspend fun updateConversationMeta(
        userId: String,
        conversationId: String,
        title: String,
        modelOption: AiModelOption,
        studyMode: StudyMode,
        lastMessagePreview: String
    ): Result<Unit>

    suspend fun deleteConversation(userId: String, conversationId: String): Result<Unit>

    suspend fun addMessage(
        userId: String,
        conversationId: String,
        role: String,
        text: String,
        modelUsed: String,
        hasImage: Boolean = false
    ): Result<Unit>

    fun observeHomeworkTasks(userId: String): Flow<List<HomeworkTask>>

    suspend fun addHomeworkTask(
        userId: String,
        title: String,
        subject: String,
        notes: String,
        priority: TaskPriority,
        dueAtMillis: Long,
        reminderEnabled: Boolean,
        reminderAtMillis: Long
    ): Result<String>

    suspend fun updateHomeworkTask(
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
    ): Result<Unit>

    suspend fun toggleHomeworkCompleted(
        userId: String,
        taskId: String,
        completed: Boolean
    ): Result<Unit>

    suspend fun deleteHomeworkTask(userId: String, taskId: String): Result<Unit>

    fun observeFlashcardDecks(userId: String): Flow<List<FlashcardDeck>>

    suspend fun saveFlashcardDeck(
        userId: String,
        title: String,
        subject: String,
        cards: List<FlashcardItem>
    ): Result<String>

    suspend fun updateFlashcardDeckCards(
        userId: String,
        deckId: String,
        cards: List<FlashcardItem>
    ): Result<Unit>

    suspend fun deleteFlashcardDeck(userId: String, deckId: String): Result<Unit>

    fun observeStudySessions(userId: String): Flow<List<StudySession>>

    suspend fun logStudySession(
        userId: String,
        technique: StudyTechnique,
        topic: String,
        durationMinutes: Long,
        scoreOrRating: Long,
        summary: String
    ): Result<Unit>
}
