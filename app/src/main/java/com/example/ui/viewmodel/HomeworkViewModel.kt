package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GeminiAiRepository
import com.example.data.StudyDataRepository
import com.example.model.AiModelOption
import com.example.model.HomeworkTask
import com.example.model.TaskPriority
import com.example.model.UiState
import com.example.notifications.HomeworkReminderScheduler
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "HomeworkViewModel"
private const val STOP_TIMEOUT_MS = 5000L

enum class HomeworkFilter(val label: String) {
    PENDING("Pendientes"),
    ALL("Todas"),
    COMPLETED("Completadas")
}

class HomeworkViewModel(
    private val firestoreRepository: StudyDataRepository,
    private val aiRepository: GeminiAiRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _selectedFilter = MutableStateFlow(HomeworkFilter.PENDING)
    val selectedFilter: StateFlow<HomeworkFilter> = _selectedFilter.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _studyPlanState = MutableStateFlow<UiState<Pair<String, String>>?>(null)
    val studyPlanState: StateFlow<UiState<Pair<String, String>>?> = _studyPlanState.asStateFlow()

    val tasksState: StateFlow<UiState<List<HomeworkTask>>> =
        firestoreRepository.observeHomeworkTasks(currentUserId)
            .map<List<HomeworkTask>, UiState<List<HomeworkTask>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing homework tasks", rootError)
                emit(UiState.Error(rootError.message ?: "No se pudieron cargar tus tareas."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    fun setFilter(filter: HomeworkFilter) {
        _selectedFilter.value = filter
    }

    fun clearMessage() {
        _actionMessage.value = null
    }

    fun clearStudyPlan() {
        _studyPlanState.value = null
    }

    fun saveTask(
        context: Context,
        existingTask: HomeworkTask?,
        title: String,
        subject: String,
        notes: String,
        priority: TaskPriority,
        dueAtMillis: Long,
        reminderEnabled: Boolean,
        reminderAtMillis: Long
    ) {
        if (title.isBlank()) {
            _actionMessage.value = "Escribe el título de la tarea."
            return
        }
        viewModelScope.launch {
            if (existingTask == null) {
                firestoreRepository.addHomeworkTask(
                    userId = currentUserId,
                    title = title,
                    subject = subject,
                    notes = notes,
                    priority = priority,
                    dueAtMillis = dueAtMillis,
                    reminderEnabled = reminderEnabled,
                    reminderAtMillis = reminderAtMillis
                ).onSuccess { newId ->
                    if (reminderEnabled && reminderAtMillis > System.currentTimeMillis()) {
                        HomeworkReminderScheduler.scheduleReminder(
                            context = context,
                            taskId = newId,
                            title = title.trim(),
                            subject = subject.trim().ifEmpty { "General" },
                            triggerAtMillis = reminderAtMillis
                        )
                        _actionMessage.value = "Tarea guardada con recordatorio programado."
                    } else {
                        _actionMessage.value = "Tarea guardada."
                    }
                }.onFailure { e ->
                    _actionMessage.value = e.localizedMessage ?: "Error al guardar la tarea."
                }
            } else {
                firestoreRepository.updateHomeworkTask(
                    userId = currentUserId,
                    taskId = existingTask.id,
                    title = title,
                    subject = subject,
                    notes = notes,
                    priority = priority,
                    dueAtMillis = dueAtMillis,
                    reminderEnabled = reminderEnabled,
                    reminderAtMillis = reminderAtMillis,
                    completed = existingTask.completed
                ).onSuccess {
                    if (reminderEnabled && !existingTask.completed && reminderAtMillis > System.currentTimeMillis()) {
                        HomeworkReminderScheduler.scheduleReminder(
                            context = context,
                            taskId = existingTask.id,
                            title = title.trim(),
                            subject = subject.trim().ifEmpty { "General" },
                            triggerAtMillis = reminderAtMillis
                        )
                    } else {
                        HomeworkReminderScheduler.cancelReminder(context, existingTask.id)
                    }
                    _actionMessage.value = "Tarea actualizada."
                }.onFailure { e ->
                    _actionMessage.value = e.localizedMessage ?: "Error al actualizar la tarea."
                }
            }
        }
    }

    fun toggleCompleted(context: Context, task: HomeworkTask) {
        val nextCompleted = !task.completed
        viewModelScope.launch {
            firestoreRepository.toggleHomeworkCompleted(currentUserId, task.id, nextCompleted)
                .onSuccess {
                    if (nextCompleted) {
                        HomeworkReminderScheduler.cancelReminder(context, task.id)
                    } else if (task.reminderEnabled && task.reminderAtMillis > System.currentTimeMillis()) {
                        HomeworkReminderScheduler.scheduleReminder(
                            context = context,
                            taskId = task.id,
                            title = task.title,
                            subject = task.subject,
                            triggerAtMillis = task.reminderAtMillis
                        )
                    }
                }
                .onFailure { e ->
                    _actionMessage.value = e.localizedMessage ?: "No se pudo actualizar el estado."
                }
        }
    }

    fun deleteTask(context: Context, taskId: String) {
        viewModelScope.launch {
            HomeworkReminderScheduler.cancelReminder(context, taskId)
            firestoreRepository.deleteHomeworkTask(currentUserId, taskId)
                .onSuccess { _actionMessage.value = "Tarea eliminada." }
                .onFailure { e ->
                    _actionMessage.value = e.localizedMessage ?: "No se pudo eliminar la tarea."
                }
        }
    }

    fun sendTestReminder(context: Context, task: HomeworkTask) {
        HomeworkReminderScheduler.showNotificationNow(
            context = context,
            taskId = task.id,
            title = task.title,
            subject = task.subject
        )
        _actionMessage.value = "Notificación de recordatorio enviada."
    }

    fun generateStudyPlanForTask(task: HomeworkTask) {
        viewModelScope.launch {
            _studyPlanState.value = UiState.Loading
            aiRepository.suggestHomeworkStudyPlan(
                modelOption = AiModelOption.GEMINI_3_8_FLASH,
                taskTitle = task.title,
                subject = task.subject,
                notes = task.notes
            ).onSuccess { plan ->
                _studyPlanState.value = UiState.Success(task.title to plan)
            }.onFailure { e ->
                _studyPlanState.value = UiState.Error(
                    e.localizedMessage ?: "No se pudo generar el plan de estudio."
                )
            }
        }
    }
}
