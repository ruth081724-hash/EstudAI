package com.example.ui.screens

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.HomeworkTask
import com.example.model.TaskPriority
import com.example.model.UiState
import com.example.ui.viewmodel.HomeworkFilter
import com.example.ui.viewmodel.HomeworkViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeworkScreen(
    viewModel: HomeworkViewModel,
    onAskAiForTask: (HomeworkTask) -> Unit,
    onShowSnackbar: (String) -> Unit
) {
    val context = LocalContext.current
    val tasksState by viewModel.tasksState.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()
    val studyPlanState by viewModel.studyPlanState.collectAsStateWithLifecycle()

    var showEditorSheet by remember { mutableStateOf(false) }
    var taskBeingEdited by remember { mutableStateOf<HomeworkTask?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            onShowSnackbar("Activa el permiso de notificaciones en Ajustes para recibir recordatorios.")
        }
    }

    LaunchedEffect(actionMessage) {
        val msg = actionMessage
        if (msg != null) {
            onShowSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    taskBeingEdited = null
                    showEditorSheet = true
                },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Nueva tarea") },
                modifier = Modifier.testTag("add_homework_fab")
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = tasksState) {
                is UiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                is UiState.Success -> {
                    val allTasks = state.data
                    val pendingCount = allTasks.count { !it.completed }
                    val remindersCount = allTasks.count { !it.completed && it.reminderEnabled }
                    val completedCount = allTasks.count { it.completed }

                    val filtered = when (selectedFilter) {
                        HomeworkFilter.PENDING -> allTasks.filter { !it.completed }
                        HomeworkFilter.ALL -> allTasks
                        HomeworkFilter.COMPLETED -> allTasks.filter { it.completed }
                    }

                    // Summary KPI Banner
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            HomeworkStatPill("Pendientes", pendingCount.toString())
                            HomeworkStatPill("Con aviso", remindersCount.toString())
                            HomeworkStatPill("Hechas", completedCount.toString())
                        }
                    }

                    // Filter Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HomeworkFilter.entries.forEach { filter ->
                            FilterChip(
                                selected = selectedFilter == filter,
                                onClick = { viewModel.setFilter(filter) },
                                label = { Text(filter.label) },
                                modifier = Modifier.testTag("filter_${filter.name.lowercase()}")
                            )
                        }
                    }

                    if (filtered.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_empty_tasks),
                                contentDescription = "Sin tareas pendientes",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(180.dp, 135.dp)
                                    .clip(RoundedCornerShape(20.dp))
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (selectedFilter == HomeworkFilter.PENDING) {
                                    "¡Todo al día!"
                                } else {
                                    "No hay tareas aquí"
                                },
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Agrega tus tareas y activa un recordatorio para estudiar sin estrés.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = 92.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(filtered, key = { idx, task -> task.id.ifEmpty { "task_$idx" } }) { _, task ->
                                HomeworkTaskCard(
                                    task = task,
                                    onToggleComplete = { viewModel.toggleCompleted(context, task) },
                                    onEdit = {
                                        taskBeingEdited = task
                                        showEditorSheet = true
                                    },
                                    onDelete = { viewModel.deleteTask(context, task.id) },
                                    onAskAi = { onAskAiForTask(task) },
                                    onGeneratePlan = { viewModel.generateStudyPlanForTask(task) },
                                    onTestReminder = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                            ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.POST_NOTIFICATIONS
                                            ) != PackageManager.PERMISSION_GRANTED
                                        ) {
                                            notificationPermissionLauncher.launch(
                                                Manifest.permission.POST_NOTIFICATIONS
                                            )
                                        } else {
                                            viewModel.sendTestReminder(context, task)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditorSheet) {
        HomeworkEditorBottomSheet(
            existingTask = taskBeingEdited,
            onDismiss = { showEditorSheet = false },
            onRequestNotificationPermission = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onSave = { title, subject, notes, priority, dueAtMillis, reminderEnabled, reminderAtMillis ->
                if (reminderEnabled &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                viewModel.saveTask(
                    context = context,
                    existingTask = taskBeingEdited,
                    title = title,
                    subject = subject,
                    notes = notes,
                    priority = priority,
                    dueAtMillis = dueAtMillis,
                    reminderEnabled = reminderEnabled,
                    reminderAtMillis = reminderAtMillis
                )
                showEditorSheet = false
            }
        )
    }

    val planState = studyPlanState
    if (planState != null) {
        ModalBottomSheet(onDismissRequest = { viewModel.clearStudyPlan() }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Plan de estudio con IA",
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                when (planState) {
                    is UiState.Loading -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 24.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Organizando pasos y metodología ideal...")
                        }
                    }
                    is UiState.Error -> {
                        Text(
                            text = planState.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    is UiState.Success -> {
                        Text(
                            text = planState.data.first,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = planState.data.second,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun HomeworkStatPill(label: String, count: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun HomeworkTaskCard(
    task: HomeworkTask,
    onToggleComplete: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAskAi: () -> Unit,
    onGeneratePlan: () -> Unit,
    onTestReminder: () -> Unit
) {
    val priority = TaskPriority.fromName(task.priority)
    val priorityContainer = when (priority) {
        TaskPriority.HIGH -> MaterialTheme.colorScheme.errorContainer
        TaskPriority.MEDIUM -> MaterialTheme.colorScheme.secondaryContainer
        TaskPriority.LOW -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val priorityTextColor = when (priority) {
        TaskPriority.HIGH -> MaterialTheme.colorScheme.onErrorContainer
        TaskPriority.MEDIUM -> MaterialTheme.colorScheme.onSecondaryContainer
        TaskPriority.LOW -> MaterialTheme.colorScheme.onTertiaryContainer
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                IconButton(
                    onClick = onToggleComplete,
                    modifier = Modifier.testTag("toggle_task_${task.id}")
                ) {
                    Icon(
                        imageVector = if (task.completed) {
                            Icons.Rounded.CheckCircle
                        } else {
                            Icons.Rounded.RadioButtonUnchecked
                        },
                        contentDescription = if (task.completed) "Marcar como pendiente" else "Completar tarea",
                        tint = if (task.completed) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(
                                text = task.subject,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Surface(
                            color = priorityContainer,
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(
                                text = "Prioridad ${priority.label}",
                                style = MaterialTheme.typography.labelSmall,
                                color = priorityTextColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None
                        ),
                        color = if (task.completed) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    if (task.notes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = task.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Entrega: ${formatShortDateTime(task.dueAtMillis)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (task.reminderEnabled && task.reminderAtMillis > 0L) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { onTestReminder() }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.NotificationsActive,
                                    contentDescription = "Probar recordatorio",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = formatShortDateTime(task.reminderAtMillis),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                IconButton(onClick = onEdit) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Editar tarea")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Eliminar tarea")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onAskAi,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Resolver con IA", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = onGeneratePlan,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.School,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Plan de estudio", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeworkEditorBottomSheet(
    existingTask: HomeworkTask?,
    onDismiss: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onSave: (
        title: String,
        subject: String,
        notes: String,
        priority: TaskPriority,
        dueAtMillis: Long,
        reminderEnabled: Boolean,
        reminderAtMillis: Long
    ) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(existingTask?.title ?: "") }
    var subject by remember { mutableStateOf(existingTask?.subject ?: "Matemáticas") }
    var notes by remember { mutableStateOf(existingTask?.notes ?: "") }
    var priority by remember {
        mutableStateOf(TaskPriority.fromName(existingTask?.priority))
    }
    var dueAtMillis by remember {
        mutableLongStateOf(
            existingTask?.dueAtMillis ?: (System.currentTimeMillis() + 86_400_000L)
        )
    }
    var reminderEnabled by remember {
        mutableStateOf(existingTask?.reminderEnabled ?: true)
    }
    var reminderAtMillis by remember {
        mutableLongStateOf(
            existingTask?.reminderAtMillis?.takeIf { it > 0L }
                ?: (System.currentTimeMillis() + 3_600_000L)
        )
    }

    val quickSubjects = listOf("Matemáticas", "Historia", "Ciencias", "Lenguaje", "Inglés", "Programación")

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Text(
                text = if (existingTask == null) "Nueva tarea y recordatorio" else "Editar tarea",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Título de la tarea") },
                placeholder = { Text("Ej. Resolver guía de ecuaciones") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("homework_title_input")
            )

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickSubjects.forEach { item ->
                    FilterChip(
                        selected = subject.equals(item, ignoreCase = true),
                        onClick = { subject = item },
                        label = { Text(item) }
                    )
                }
            }
            OutlinedTextField(
                value = subject,
                onValueChange = { subject = it },
                label = { Text("Materia") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Detalles o páginas (opcional)") },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))
            Text("Prioridad", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskPriority.entries.forEach { p ->
                    FilterChip(
                        selected = priority == p,
                        onClick = { priority = p },
                        label = { Text(p.label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Fecha de entrega", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = formatShortDateTime(dueAtMillis),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                TextButton(
                    onClick = {
                        pickDateTime(context, dueAtMillis) { chosen ->
                            dueAtMillis = chosen
                        }
                    }
                ) {
                    Text("Cambiar")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (reminderEnabled) {
                            Icons.Rounded.NotificationsActive
                        } else {
                            Icons.Rounded.NotificationsOff
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Recordatorio en notificación", style = MaterialTheme.typography.titleSmall)
                        if (reminderEnabled) {
                            Text(
                                text = "Aviso: ${formatShortDateTime(reminderAtMillis)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                Switch(
                    checked = reminderEnabled,
                    onCheckedChange = { enabled ->
                        reminderEnabled = enabled
                        if (enabled) {
                            onRequestNotificationPermission()
                        }
                    },
                    modifier = Modifier.testTag("reminder_switch")
                )
            }

            if (reminderEnabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = false,
                        onClick = { reminderAtMillis = System.currentTimeMillis() + 15 * 60_000L },
                        label = { Text("En 15 min") }
                    )
                    FilterChip(
                        selected = false,
                        onClick = { reminderAtMillis = System.currentTimeMillis() + 60 * 60_000L },
                        label = { Text("En 1 hora") }
                    )
                    FilterChip(
                        selected = false,
                        onClick = {
                            reminderAtMillis = (dueAtMillis - 3_600_000L)
                                .coerceAtLeast(System.currentTimeMillis() + 60_000L)
                        },
                        label = { Text("1h antes de entrega") }
                    )
                    FilterChip(
                        selected = false,
                        onClick = {
                            pickDateTime(context, reminderAtMillis) { chosen ->
                                reminderAtMillis = chosen
                            }
                        },
                        label = { Text("Elegir hora") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            Button(
                onClick = {
                    onSave(
                        title,
                        subject,
                        notes,
                        priority,
                        dueAtMillis,
                        reminderEnabled,
                        reminderAtMillis
                    )
                },
                enabled = title.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_homework_button")
            ) {
                Text(
                    text = if (existingTask == null) "Guardar tarea" else "Actualizar tarea",
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

private fun pickDateTime(
    context: android.content.Context,
    initialMillis: Long,
    onPicked: (Long) -> Unit
) {
    val cal = Calendar.getInstance().apply { timeInMillis = initialMillis }
    DatePickerDialog(
        context,
        { _, year, month, day ->
            cal.set(Calendar.YEAR, year)
            cal.set(Calendar.MONTH, month)
            cal.set(Calendar.DAY_OF_MONTH, day)
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    cal.set(Calendar.HOUR_OF_DAY, hour)
                    cal.set(Calendar.MINUTE, minute)
                    cal.set(Calendar.SECOND, 0)
                    onPicked(cal.timeInMillis)
                },
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                true
            ).show()
        },
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH),
        cal.get(Calendar.DAY_OF_MONTH)
    ).show()
}

private fun formatShortDateTime(millis: Long): String {
    val formatter = SimpleDateFormat("d MMM, HH:mm", Locale.forLanguageTag("es-ES"))
    return formatter.format(Date(millis))
}
