package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GeminiAiRepository
import com.example.data.StudyDataRepository
import com.example.model.AiModelOption
import com.example.model.ChatMessage
import com.example.model.Conversation
import com.example.model.StudyMode
import com.example.model.UiState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "ChatViewModel"
private const val STOP_TIMEOUT_MS = 5000L

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val firestoreRepository: StudyDataRepository,
    private val aiRepository: GeminiAiRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _selectedModel = MutableStateFlow(AiModelOption.GEMINI_3_8_FLASH)
    val selectedModel: StateFlow<AiModelOption> = _selectedModel.asStateFlow()

    private val _selectedStudyMode = MutableStateFlow(StudyMode.TUTOR)
    val selectedStudyMode: StateFlow<StudyMode> = _selectedStudyMode.asStateFlow()

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    private val _streamingReply = MutableStateFlow<String?>(null)
    val streamingReply: StateFlow<String?> = _streamingReply.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _voiceAutoReply = MutableStateFlow(false)
    val voiceAutoReply: StateFlow<Boolean> = _voiceAutoReply.asStateFlow()

    private val _draftInput = MutableStateFlow("")
    val draftInput: StateFlow<String> = _draftInput.asStateFlow()

    private val _attachedBitmap = MutableStateFlow<Bitmap?>(null)
    val attachedBitmap: StateFlow<Bitmap?> = _attachedBitmap.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _lastCompletedReplyForTts = MutableStateFlow<Pair<String, String>?>(null)
    val lastCompletedReplyForTts: StateFlow<Pair<String, String>?> = _lastCompletedReplyForTts.asStateFlow()

    val conversationsState: StateFlow<UiState<List<Conversation>>> =
        firestoreRepository.observeConversations(currentUserId)
            .map<List<Conversation>, UiState<List<Conversation>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing conversations", rootError)
                emit(UiState.Error(rootError.message ?: "No se pudieron cargar las conversaciones."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    val messagesState: StateFlow<UiState<List<ChatMessage>>> =
        _activeConversationId.flatMapLatest { convId ->
            if (convId == null) {
                flowOf(UiState.Success(emptyList()))
            } else {
                firestoreRepository.observeMessages(currentUserId, convId)
                    .map<List<ChatMessage>, UiState<List<ChatMessage>>> { UiState.Success(it) }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val rootError = firestoreError ?: error
                        Log.w(TAG, "Error observing messages", rootError)
                        emit(UiState.Error(rootError.message ?: "No se pudieron cargar los mensajes."))
                    }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Success(emptyList())
        )

    fun selectModel(option: AiModelOption) {
        _selectedModel.value = option
    }

    fun selectStudyMode(mode: StudyMode) {
        _selectedStudyMode.value = mode
    }

    fun toggleVoiceAutoReply() {
        _voiceAutoReply.value = !_voiceAutoReply.value
    }

    fun setDraftInput(text: String) {
        _draftInput.value = text
    }

    fun setAttachedBitmap(bitmap: Bitmap?) {
        _attachedBitmap.value = bitmap
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun consumeTtsEvent() {
        _lastCompletedReplyForTts.value = null
    }

    fun startNewChat() {
        _activeConversationId.value = null
        _streamingReply.value = null
        _errorMessage.value = null
    }

    fun openConversation(conversation: Conversation) {
        _activeConversationId.value = conversation.id
        _selectedModel.value = AiModelOption.fromModelId(conversation.modelId)
        _selectedStudyMode.value = StudyMode.fromName(conversation.studyMode)
        _errorMessage.value = null
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            if (_activeConversationId.value == conversationId) {
                _activeConversationId.value = null
            }
            firestoreRepository.deleteConversation(currentUserId, conversationId)
                .onFailure { e ->
                    _errorMessage.value = e.localizedMessage ?: "No se pudo eliminar el chat."
                }
        }
    }

    fun askAboutHomework(title: String, subject: String, notes: String) {
        startNewChat()
        _selectedStudyMode.value = StudyMode.TUTOR
        val prompt = buildString {
            append("Ayúdame con esta tarea de $subject: \"$title\".")
            if (notes.isNotBlank()) {
                append(" Detalles: $notes.")
            }
            append(" Explícamelo paso a paso y dime cómo estudiarlo mejor.")
        }
        sendMessage(prompt)
    }

    fun sendMessage(overridePrompt: String? = null) {
        if (_isGenerating.value) return
        val rawPrompt = (overridePrompt ?: _draftInput.value).trim()
        val bitmapToSend = _attachedBitmap.value
        if (rawPrompt.isEmpty() && bitmapToSend == null) return

        val prompt = rawPrompt.ifEmpty { "Analiza esta imagen de mi tarea y explícamela paso a paso." }
        if (overridePrompt == null) {
            _draftInput.value = ""
        }
        _attachedBitmap.value = null
        _errorMessage.value = null

        val modelUsed = _selectedModel.value
        val studyMode = _selectedStudyMode.value
        val currentHistory = (messagesState.value as? UiState.Success)?.data ?: emptyList()

        viewModelScope.launch {
            _isGenerating.value = true
            _streamingReply.value = ""
            try {
                var convId = _activeConversationId.value
                val derivedTitle = prompt.replace("\n", " ").trim().take(54).let {
                    if (prompt.length > 54) "$it…" else it
                }
                if (convId == null) {
                    val created = firestoreRepository.createConversation(
                        userId = currentUserId,
                        title = derivedTitle,
                        modelOption = modelUsed,
                        studyMode = studyMode,
                        lastMessagePreview = prompt
                    )
                    convId = created.getOrThrow()
                    _activeConversationId.value = convId
                }

                firestoreRepository.addMessage(
                    userId = currentUserId,
                    conversationId = convId,
                    role = "user",
                    text = prompt,
                    modelUsed = modelUsed.modelId,
                    hasImage = bitmapToSend != null
                ).getOrThrow()

                val finalBuffer = StringBuilder()
                aiRepository.streamChatReply(
                    modelOption = modelUsed,
                    studyMode = studyMode,
                    history = currentHistory,
                    userPrompt = prompt,
                    attachedBitmap = bitmapToSend
                ).collect { accumulatedText ->
                    finalBuffer.clear()
                    finalBuffer.append(accumulatedText)
                    _streamingReply.value = accumulatedText
                }

                val replyText = finalBuffer.toString().trim()
                if (replyText.isNotEmpty()) {
                    firestoreRepository.addMessage(
                        userId = currentUserId,
                        conversationId = convId,
                        role = "model",
                        text = replyText,
                        modelUsed = modelUsed.modelId,
                        hasImage = false
                    ).getOrThrow()
                    firestoreRepository.updateConversationMeta(
                        userId = currentUserId,
                        conversationId = convId,
                        title = currentHistory.firstOrNull()?.text?.replace("\n", " ")?.trim()?.take(54) ?: derivedTitle,
                        modelOption = modelUsed,
                        studyMode = studyMode,
                        lastMessagePreview = replyText.take(280)
                    ).onFailure { e ->
                        Log.w(TAG, "Failed to update conversation metadata", e)
                    }
                    if (_voiceAutoReply.value) {
                        _lastCompletedReplyForTts.value = convId to replyText
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error in sendMessage", e)
                _errorMessage.value = e.localizedMessage
                    ?: "No se pudo obtener respuesta de Gemini. Revisa tu conexión."
            } finally {
                _streamingReply.value = null
                _isGenerating.value = false
            }
        }
    }
}
