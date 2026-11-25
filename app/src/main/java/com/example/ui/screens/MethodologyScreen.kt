package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.FeynmanEvaluation
import com.example.model.UiState
import com.example.ui.theme.MonoTimerStyle
import com.example.ui.viewmodel.MethodologyViewModel
import com.example.ui.viewmodel.PomodoroPreset
import com.example.voice.VoiceAssistantManager
import java.util.Locale

enum class MethodologyTab(val label: String, val icon: ImageVector) {
    POMODORO("Pomodoro", Icons.Rounded.Timer),
    FEYNMAN("Feynman", Icons.Rounded.Psychology),
    FLASHCARDS("Flashcards", Icons.Rounded.Style),
    CORNELL("Cornell", Icons.Rounded.Description),
    QUIZ("Quiz IA", Icons.Rounded.Quiz),
    PROGRESS("Progreso", Icons.Rounded.BarChart)
}

@Composable
fun MethodologyScreen(
    viewModel: MethodologyViewModel,
    voiceManager: VoiceAssistantManager,
    onShowSnackbar: (String) -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(MethodologyTab.POMODORO) }
    var feynmanTopic by rememberSaveable { mutableStateOf("") }
    var feynmanExplanation by rememberSaveable { mutableStateOf("") }
    var flashcardTopic by rememberSaveable { mutableStateOf("") }
    var flashcardSubject by rememberSaveable { mutableStateOf("General") }
    var cornellTopic by rememberSaveable { mutableStateOf("") }
    var cornellNotes by rememberSaveable { mutableStateOf("") }
    var quizTopic by rememberSaveable { mutableStateOf("") }

    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    LaunchedEffect(statusMessage) {
        val msg = statusMessage
        if (msg != null) {
            onShowSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MethodologyTab.entries.forEach { tab ->
                FilterChip(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    label = { Text(tab.label) },
                    leadingIcon = {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier.testTag("method_tab_${tab.name.lowercase()}")
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                MethodologyTab.POMODORO -> PomodoroSection(viewModel)
                MethodologyTab.FEYNMAN -> FeynmanSection(
                    viewModel = viewModel,
                    voiceManager = voiceManager,
                    topic = feynmanTopic,
                    onTopicChange = { feynmanTopic = it },
                    explanation = feynmanExplanation,
                    onExplanationChange = { feynmanExplanation = it },
                    onShowSnackbar = onShowSnackbar
                )
                MethodologyTab.FLASHCARDS -> FlashcardsSection(
                    viewModel = viewModel,
                    topic = flashcardTopic,
                    onTopicChange = { flashcardTopic = it },
                    subject = flashcardSubject,
                    onSubjectChange = { flashcardSubject = it }
                )
                MethodologyTab.CORNELL -> CornellSection(
                    viewModel = viewModel,
                    topic = cornellTopic,
                    onTopicChange = { cornellTopic = it },
                    rawNotes = cornellNotes,
                    onRawNotesChange = { cornellNotes = it }
                )
                MethodologyTab.QUIZ -> QuizSection(
                    viewModel = viewModel,
                    topic = quizTopic,
                    onTopicChange = { quizTopic = it }
                )
                MethodologyTab.PROGRESS -> ProgressSection(viewModel)
            }
        }
    }
}

@Composable
private fun PomodoroSection(viewModel: MethodologyViewModel) {
    val preset by viewModel.pomodoroPreset.collectAsStateWithLifecycle()
    val isBreak by viewModel.pomodoroIsBreak.collectAsStateWithLifecycle()
    val remainingSec by viewModel.pomodoroRemainingSec.collectAsStateWithLifecycle()
    val isRunning by viewModel.pomodoroRunning.collectAsStateWithLifecycle()
    val topic by viewModel.pomodoroTopic.collectAsStateWithLifecycle()

    val totalSec = if (isBreak) preset.breakMinutes * 60 else preset.focusMinutes * 60
    val progress = if (totalSec > 0) remainingSec.toFloat() / totalSec.toFloat() else 0f
    val minutes = remainingSec / 60
    val seconds = remainingSec % 60
    val formattedTime = String.format(Locale.US, "%02d:%02d", minutes, seconds)

    val ringTrackColor = MaterialTheme.colorScheme.surfaceVariant
    val ringActiveColor = if (isBreak) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.primary
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            color = if (isBreak) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
            shape = RoundedCornerShape(999.dp)
        ) {
            Text(
                text = if (isBreak) "Tiempo de descanso" else "Bloque de enfoque profundo",
                style = MaterialTheme.typography.labelLarge,
                color = if (isBreak) {
                    MaterialTheme.colorScheme.onTertiaryContainer
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                },
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(220.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                val stroke = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
                drawArc(
                    color = ringTrackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = stroke
                )
                drawArc(
                    color = ringActiveColor,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    style = stroke
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formattedTime,
                    style = MonoTimerStyle,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = preset.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PomodoroPreset.entries.forEach { option ->
                FilterChip(
                    selected = preset == option,
                    onClick = { viewModel.setPomodoroPreset(option) },
                    label = { Text(option.label) }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = topic,
            onValueChange = { viewModel.setPomodoroTopic(it) },
            label = { Text("¿Qué estás estudiando en este bloque?") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { viewModel.togglePomodoro() },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("pomodoro_toggle_button")
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isRunning) "Pausar" else "Iniciar")
            }

            OutlinedButton(
                onClick = { viewModel.resetPomodoro() },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.height(50.dp)
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Reiniciar")
            }

            OutlinedButton(
                onClick = { viewModel.completePomodoroEarly() },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.height(50.dp)
            ) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = "Registrar bloque")
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("¿Por qué funciona Pomodoro?", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Alternar bloques de concentración sin distracciones con descansos breves reduce la fatiga mental y consolida la memoria de trabajo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FeynmanSection(
    viewModel: MethodologyViewModel,
    voiceManager: VoiceAssistantManager,
    topic: String,
    onTopicChange: (String) -> Unit,
    explanation: String,
    onExplanationChange: (String) -> Unit,
    onShowSnackbar: (String) -> Unit
) {
    val context = LocalContext.current
    val feynmanState by viewModel.feynmanState.collectAsStateWithLifecycle()
    val isListening by voiceManager.isListening.collectAsStateWithLifecycle()

    val startDictation = {
        voiceManager.startListening(
            onFinalResult = { spoken ->
                onExplanationChange(if (explanation.isBlank()) spoken else "$explanation $spoken")
            },
            onError = { err -> onShowSnackbar(err) }
        )
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startDictation() else onShowSnackbar("Permiso de micrófono requerido para dictar.")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text("Técnica Feynman", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "Explica un tema como si se lo enseñaras a alguien de 12 años. Gemini detectará tus fortalezas y lagunas.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(14.dp))
        OutlinedTextField(
            value = topic,
            onValueChange = onTopicChange,
            label = { Text("Tema que estás estudiando") },
            placeholder = { Text("Ej. La mitosis celular o Ley de Ohm") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("feynman_topic_input")
        )

        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
            value = explanation,
            onValueChange = onExplanationChange,
            label = { Text("Tu explicación con palabras simples") },
            placeholder = { Text("Escribe o dicta usando el micrófono...") },
            minLines = 4,
            maxLines = 8,
            trailingIcon = {
                IconButton(
                    onClick = {
                        if (isListening) {
                            voiceManager.stopListening()
                        } else {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) startDictation()
                            else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                        contentDescription = "Dictar explicación"
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("feynman_explanation_input")
        )

        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = { viewModel.evaluateFeynman(topic, explanation) },
            enabled = feynmanState !is UiState.Loading,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("feynman_evaluate_button")
        ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Evaluar comprensión con IA", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))
        when (val state = feynmanState) {
            is UiState.Loading -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Analizando claridad y conceptos clave...")
                }
            }
            is UiState.Error -> {
                Text(state.message, color = MaterialTheme.colorScheme.error)
            }
            is UiState.Success -> {
                FeynmanResultCard(state.data)
            }
            null -> Unit
        }
    }
}

@Composable
private fun FeynmanResultCard(eval: FeynmanEvaluation) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Resultado Feynman", style = MaterialTheme.typography.titleLarge)
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = "${eval.score}/100",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }

            if (eval.masteredPoints.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Lo que dominaste",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
                eval.masteredPoints.forEach { pt ->
                    Text("• $pt", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 3.dp))
                }
            }

            if (eval.gaps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Lagunas por reforzar",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                eval.gaps.forEach { gap ->
                    Text("• $gap", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 3.dp))
                }
            }

            if (eval.simpleAnalogy.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Analogía memorable", style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(eval.simpleAnalogy, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (eval.nextStepTip.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Siguiente paso: ${eval.nextStepTip}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
