package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GeminiAiRepository
import com.example.data.StudyDataRepository
import com.example.model.AiModelOption
import com.example.model.CornellNotesResult
import com.example.model.FeynmanEvaluation
import com.example.model.FlashcardDeck
import com.example.model.FlashcardItem
import com.example.model.PracticeQuestion
import com.example.model.StudySession
import com.example.model.StudyTechnique
import com.example.model.UiState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "MethodologyViewModel"
private const val STOP_TIMEOUT_MS = 5000L

enum class PomodoroPreset(
    val label: String,
    val focusMinutes: Int,
    val breakMinutes: Int
) {
    CLASSIC("25 / 5 min", 25, 5),
    DEEP("50 / 10 min", 50, 10),
    SPRINT("15 / 3 min", 15, 3)
}

class MethodologyViewModel(
    private val firestoreRepository: StudyDataRepository,
    private val aiRepository: GeminiAiRepository,
    private val currentUserId: String
) : ViewModel() {

    // --- Pomodoro State ---
    private val _pomodoroPreset = MutableStateFlow(PomodoroPreset.CLASSIC)
    val pomodoroPreset: StateFlow<PomodoroPreset> = _pomodoroPreset.asStateFlow()

    private val _pomodoroIsBreak = MutableStateFlow(false)
    val pomodoroIsBreak: StateFlow<Boolean> = _pomodoroIsBreak.asStateFlow()

    private val _pomodoroRemainingSec = MutableStateFlow(PomodoroPreset.CLASSIC.focusMinutes * 60)
    val pomodoroRemainingSec: StateFlow<Int> = _pomodoroRemainingSec.asStateFlow()

    private val _pomodoroRunning = MutableStateFlow(false)
    val pomodoroRunning: StateFlow<Boolean> = _pomodoroRunning.asStateFlow()

    private val _pomodoroTopic = MutableStateFlow("Sesión de concentración")
    val pomodoroTopic: StateFlow<String> = _pomodoroTopic.asStateFlow()

    private var timerJob: Job? = null

    // --- Feynman State ---
    private val _feynmanState = MutableStateFlow<UiState<FeynmanEvaluation>?>(null)
    val feynmanState: StateFlow<UiState<FeynmanEvaluation>?> = _feynmanState.asStateFlow()

    // --- Cornell State ---
    private val _cornellState = MutableStateFlow<UiState<CornellNotesResult>?>(null)
    val cornellState: StateFlow<UiState<CornellNotesResult>?> = _cornellState.asStateFlow()

    // --- Quiz State ---
    private val _quizState = MutableStateFlow<UiState<List<PracticeQuestion>>?>(null)
    val quizState: StateFlow<UiState<List<PracticeQuestion>>?> = _quizState.asStateFlow()

    // --- Flashcards Generation State ---
    private val _flashcardGenState = MutableStateFlow<UiState<String>?>(null)
    val flashcardGenState: StateFlow<UiState<String>?> = _flashcardGenState.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    val flashcardDecksState: StateFlow<UiState<List<FlashcardDeck>>> =
        firestoreRepository.observeFlashcardDecks(currentUserId)
            .map<List<FlashcardDeck>, UiState<List<FlashcardDeck>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing flashcard decks", rootError)
                emit(UiState.Error(rootError.message ?: "No se pudieron cargar tus mazos."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    val sessionsState: StateFlow<UiState<List<StudySession>>> =
        firestoreRepository.observeStudySessions(currentUserId)
            .map<List<StudySession>, UiState<List<StudySession>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing study sessions", rootError)
                emit(UiState.Error(rootError.message ?: "No se pudo cargar tu historial."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    // --- Pomodoro Actions ---

    fun setPomodoroPreset(preset: PomodoroPreset) {
        pausePomodoro()
        _pomodoroPreset.value = preset
        _pomodoroIsBreak.value = false
        _pomodoroRemainingSec.value = preset.focusMinutes * 60
    }

    fun setPomodoroTopic(topic: String) {
        _pomodoroTopic.value = topic
    }

    fun togglePomodoro() {
        if (_pomodoroRunning.value) {
            pausePomodoro()
        } else {
            startPomodoro()
        }
    }

    private fun startPomodoro() {
        timerJob?.cancel()
        _pomodoroRunning.value = true
        timerJob = viewModelScope.launch {
            while (_pomodoroRemainingSec.value > 0 && _pomodoroRunning.value) {
                delay(1000L)
                _pomodoroRemainingSec.value = (_pomodoroRemainingSec.value - 1).coerceAtLeast(0)
            }
            if (_pomodoroRemainingSec.value == 0 && _pomodoroRunning.value) {
                _pomodoroRunning.value = false
                val wasBreak = _pomodoroIsBreak.value
                val preset = _pomodoroPreset.value
                if (!wasBreak) {
                    firestoreRepository.logStudySession(
                        userId = currentUserId,
                        technique = StudyTechnique.POMODORO,
                        topic = _pomodoroTopic.value.ifBlank { "Sesión Pomodoro" },
                        durationMinutes = preset.focusMinutes.toLong(),
                        scoreOrRating = 100L,
                        summary = "Bloque de enfoque de ${preset.focusMinutes} min completado."
                    )
                    _statusMessage.value = "¡Bloque Pomodoro completado! Hora de descansar ${preset.breakMinutes} min."
                    _pomodoroIsBreak.value = true
                    _pomodoroRemainingSec.value = preset.breakMinutes * 60
                } else {
                    _statusMessage.value = "Descanso terminado. Listo para otro bloque."
                    _pomodoroIsBreak.value = false
                    _pomodoroRemainingSec.value = preset.focusMinutes * 60
                }
            }
        }
    }

    fun pausePomodoro() {
        _pomodoroRunning.value = false
        timerJob?.cancel()
    }

    fun resetPomodoro() {
        pausePomodoro()
        val preset = _pomodoroPreset.value
        _pomodoroRemainingSec.value =
            if (_pomodoroIsBreak.value) preset.breakMinutes * 60 else preset.focusMinutes * 60
    }

    fun completePomodoroEarly() {
        val preset = _pomodoroPreset.value
        val elapsedMinutes = ((preset.focusMinutes * 60 - _pomodoroRemainingSec.value) / 60).coerceAtLeast(1)
        pausePomodoro()
        _pomodoroIsBreak.value = false
        _pomodoroRemainingSec.value = preset.focusMinutes * 60
        viewModelScope.launch {
            firestoreRepository.logStudySession(
                userId = currentUserId,
                technique = StudyTechnique.POMODORO,
                topic = _pomodoroTopic.value.ifBlank { "Sesión Pomodoro" },
                durationMinutes = elapsedMinutes.toLong(),
                scoreOrRating = 100L,
                summary = "Sesión de estudio registrada ($elapsedMinutes min)."
            ).onSuccess {
                _statusMessage.value = "Sesión Pomodoro registrada ($elapsedMinutes min)."
            }
        }
    }

    // --- Feynman Technique ---

    fun evaluateFeynman(
        topic: String,
        explanation: String,
        modelOption: AiModelOption = AiModelOption.GEMINI_3_8_FLASH
    ) {
        if (topic.isBlank() || explanation.isBlank()) {
            _statusMessage.value = "Escribe el tema y tu explicación con palabras sencillas."
            return
        }
        viewModelScope.launch {
            _feynmanState.value = UiState.Loading
            aiRepository.evaluateFeynmanExplanation(modelOption, topic.trim(), explanation.trim())
                .onSuccess { eval ->
                    _feynmanState.value = UiState.Success(eval)
                    firestoreRepository.logStudySession(
                        userId = currentUserId,
                        technique = StudyTechnique.FEYNMAN,
                        topic = topic.trim(),
                        durationMinutes = 10L,
                        scoreOrRating = eval.score.toLong(),
                        summary = eval.nextStepTip
                    )
                }
                .onFailure { e ->
                    _feynmanState.value = UiState.Error(
                        e.localizedMessage ?: "No se pudo evaluar la explicación."
                    )
                }
        }
    }

    // --- Cornell Notes ---

    fun generateCornell(
        topic: String,
        rawNotes: String,
        modelOption: AiModelOption = AiModelOption.GEMINI_3_8_FLASH
    ) {
        if (topic.isBlank()) {
            _statusMessage.value = "Escribe el tema para crear los apuntes Cornell."
            return
        }
        viewModelScope.launch {
            _cornellState.value = UiState.Loading
            aiRepository.generateCornellNotes(modelOption, topic.trim(), rawNotes.trim())
                .onSuccess { notes ->
                    _cornellState.value = UiState.Success(notes)
                    firestoreRepository.logStudySession(
                        userId = currentUserId,
                        technique = StudyTechnique.CORNELL,
                        topic = notes.title,
                        durationMinutes = 10L,
                        scoreOrRating = 100L,
                        summary = notes.summary
                    )
                }
                .onFailure { e ->
                    _cornellState.value = UiState.Error(
                        e.localizedMessage ?: "No se pudieron generar los apuntes Cornell."
                    )
                }
        }
    }

    // --- Active Recall Flashcards ---

    fun generateAndSaveFlashcards(
        topic: String,
        subject: String,
        count: Int = 6,
        modelOption: AiModelOption = AiModelOption.GEMINI_3_5_FLASH_LITE
    ) {
        if (topic.isBlank()) {
            _statusMessage.value = "Escribe el tema para generar tus flashcards."
            return
        }
        viewModelScope.launch {
            _flashcardGenState.value = UiState.Loading
            aiRepository.generateFlashcardDeck(modelOption, topic.trim(), count)
                .onSuccess { (deckTitle, cards) ->
                    firestoreRepository.saveFlashcardDeck(
                        userId = currentUserId,
                        title = deckTitle,
                        subject = subject.ifBlank { "General" },
                        cards = cards
                    ).onSuccess {
                        _flashcardGenState.value = UiState.Success("Mazo \"$deckTitle\" creado (${cards.size} tarjetas).")
                        _statusMessage.value = "Mazo \"$deckTitle\" guardado."
                    }.onFailure { e ->
                        _flashcardGenState.value = UiState.Error(
                            e.localizedMessage ?: "No se pudo guardar el mazo."
                        )
                    }
                }
                .onFailure { e ->
                    _flashcardGenState.value = UiState.Error(
                        e.localizedMessage ?: "No se pudieron generar las flashcards."
                    )
                }
        }
    }

    fun rateFlashcard(deck: FlashcardDeck, cardIndex: Int, newMasteryLevel: Long) {
        if (cardIndex !in deck.cards.indices) return
        val updated = deck.cards.mapIndexed { idx, item ->
            if (idx == cardIndex) item.copy(masteryLevel = newMasteryLevel.coerceIn(0L, 5L)) else item
        }
        viewModelScope.launch {
            firestoreRepository.updateFlashcardDeckCards(currentUserId, deck.id, updated)
        }
    }

    fun deleteDeck(deckId: String) {
        viewModelScope.launch {
            firestoreRepository.deleteFlashcardDeck(currentUserId, deckId)
                .onSuccess { _statusMessage.value = "Mazo eliminado." }
        }
    }

    // --- Practice Quiz ---

    fun generateQuiz(
        topic: String,
        modelOption: AiModelOption = AiModelOption.GEMINI_3_8_FLASH
    ) {
        if (topic.isBlank()) {
            _statusMessage.value = "Escribe un tema para crear el quiz de práctica."
            return
        }
        viewModelScope.launch {
            _quizState.value = UiState.Loading
            aiRepository.generatePracticeQuiz(modelOption, topic.trim())
                .onSuccess { questions ->
                    _quizState.value = UiState.Success(questions)
                }
                .onFailure { e ->
                    _quizState.value = UiState.Error(
                        e.localizedMessage ?: "No se pudo generar el quiz."
                    )
                }
        }
    }

    fun logQuizCompletion(topic: String, correctCount: Int, totalCount: Int) {
        if (totalCount <= 0) return
        val score = ((correctCount.toFloat() / totalCount) * 100).toLong().coerceIn(0L, 100L)
        viewModelScope.launch {
            firestoreRepository.logStudySession(
                userId = currentUserId,
                technique = StudyTechnique.QUIZ,
                topic = topic.ifBlank { "Quiz de práctica" },
                durationMinutes = 5L,
                scoreOrRating = score,
                summary = "$correctCount de $totalCount respuestas correctas."
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}
