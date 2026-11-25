package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.CornellNotesResult
import com.example.model.FlashcardDeck
import com.example.model.PracticeQuestion
import com.example.model.StudyTechnique
import com.example.model.UiState
import com.example.ui.viewmodel.MethodologyViewModel

@Composable
fun FlashcardsSection(
    viewModel: MethodologyViewModel,
    topic: String,
    onTopicChange: (String) -> Unit,
    subject: String,
    onSubjectChange: (String) -> Unit
) {
    val decksState by viewModel.flashcardDecksState.collectAsStateWithLifecycle()
    val genState by viewModel.flashcardGenState.collectAsStateWithLifecycle()

    var activeDeckId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(genState) {
        if (genState is UiState.Success) {
            onTopicChange("")
        }
    }

    val decks = (decksState as? UiState.Success)?.data ?: emptyList()
    val activeDeck = decks.firstOrNull { it.id == activeDeckId }

    if (activeDeck != null) {
        BackHandler { activeDeckId = null }
        FlashcardStudyPlayer(
            deck = activeDeck,
            onBack = { activeDeckId = null },
            onRate = { idx, mastery -> viewModel.rateFlashcard(activeDeck, idx, mastery) }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text("Active Recall • Flashcards", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "Genera mazos con Gemini 3.5 Flash-Lite y repasa con repetición espaciada.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = topic,
            onValueChange = onTopicChange,
            label = { Text("Tema para tus flashcards") },
            placeholder = { Text("Ej. Verbos irregulares, Revolución Francesa") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("flashcard_topic_input")
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = subject,
            onValueChange = onSubjectChange,
            label = { Text("Materia") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = {
                viewModel.generateAndSaveFlashcards(topic, subject)
            },
            enabled = genState !is UiState.Loading,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("generate_flashcards_button")
        ) {
            if (genState is UiState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Creando mazo...")
            } else {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Crear 6 flashcards gratis", fontWeight = FontWeight.Bold)
            }
        }

        if (genState is UiState.Error) {
            Text(
                text = (genState as UiState.Error).message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text("Tus mazos guardados", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        when (decksState) {
            is UiState.Loading -> CircularProgressIndicator()
            is UiState.Error -> Text(
                text = (decksState as UiState.Error).message,
                color = MaterialTheme.colorScheme.error
            )
            is UiState.Success -> {
                if (decks.isEmpty()) {
                    Text(
                        text = "Aún no tienes mazos. Crea el primero arriba para empezar a repasar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    decks.forEach { deck ->
                        val mastered = deck.cards.count { it.masteryLevel >= 3L }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                                .clickable { activeDeckId = deck.id }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Style,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(deck.title, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = "${deck.subject} • ${deck.cards.size} tarjetas • $mastered dominadas",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { viewModel.deleteDeck(deck.id) }) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Eliminar mazo")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FlashcardStudyPlayer(
    deck: FlashcardDeck,
    onBack: () -> Unit,
    onRate: (cardIndex: Int, newMastery: Long) -> Unit
) {
    var index by remember(deck.id) { mutableIntStateOf(0) }
    var showAnswer by remember(deck.id, index) { mutableStateOf(false) }
    var showHint by remember(deck.id, index) { mutableStateOf(false) }

    val safeIndex = index.coerceIn(0, (deck.cards.size - 1).coerceAtLeast(0))
    val card = deck.cards.getOrNull(safeIndex) ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver a mazos")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(deck.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Tarjeta ${safeIndex + 1} de ${deck.cards.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (showAnswer) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.surface
                }
            ),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clickable { showAnswer = !showAnswer }
                .testTag("flashcard_flip_card")
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            text = if (showAnswer) "RESPUESTA" else "PREGUNTA",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = if (showAnswer) card.answer else card.question,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    if (!showAnswer && card.hint.isNotBlank()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        if (showHint) {
                            Text(
                                text = "Pista: ${card.hint}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            TextButton(onClick = { showHint = true }) {
                                Icon(Icons.Rounded.Lightbulb, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Ver pista")
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = if (showAnswer) "¿Qué tan fácil fue recordarlo?" else "Toca la tarjeta para ver la respuesta",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    onRate(safeIndex, 1L)
                    index = (safeIndex + 1) % deck.cards.size
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Difícil")
            }
            OutlinedButton(
                onClick = {
                    onRate(safeIndex, 3L)
                    index = (safeIndex + 1) % deck.cards.size
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Bien")
            }
            Button(
                onClick = {
                    onRate(safeIndex, 5L)
                    index = (safeIndex + 1) % deck.cards.size
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Fácil")
            }
        }
    }
}

@Composable
fun CornellSection(
    viewModel: MethodologyViewModel,
    topic: String,
    onTopicChange: (String) -> Unit,
    rawNotes: String,
    onRawNotesChange: (String) -> Unit
) {
    val cornellState by viewModel.cornellState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text("Método Cornell con IA", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "Convierte cualquier tema o apuntes desordenados en una hoja Cornell estructurada.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = topic,
            onValueChange = onTopicChange,
            label = { Text("Tema del apunte") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("cornell_topic_input")
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = rawNotes,
            onValueChange = onRawNotesChange,
            label = { Text("Pega tus apuntes previos (opcional)") },
            minLines = 3,
            maxLines = 6,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = { viewModel.generateCornell(topic, rawNotes) },
            enabled = cornellState !is UiState.Loading,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("generate_cornell_button")
        ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Estructurar apuntes Cornell", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))
        when (val state = cornellState) {
            is UiState.Loading -> CircularProgressIndicator()
            is UiState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error)
            is UiState.Success -> CornellSheetCard(state.data)
            null -> Unit
        }
    }
}

@Composable
private fun CornellSheetCard(notes: CornellNotesResult) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(notes.title, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Preguntas Clave (Columna Izquierda)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            notes.cueQuestions.forEach { q ->
                Text("? $q", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Notas Principales",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary
            )
            notes.mainNotes.forEach { n ->
                Text("• $n", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Resumen Final", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(notes.summary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun QuizSection(
    viewModel: MethodologyViewModel,
    topic: String,
    onTopicChange: (String) -> Unit
) {
    val quizState by viewModel.quizState.collectAsStateWithLifecycle()
    val selectedAnswers = remember { mutableStateMapOf<Int, Int>() }
    var submitted by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text("Quiz de Práctica con IA", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "Pon a prueba lo aprendido antes de tu examen con 5 preguntas generadas al instante.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = topic,
            onValueChange = onTopicChange,
            label = { Text("Tema del quiz") },
            placeholder = { Text("Ej. Termodinámica o Independencia de México") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("quiz_topic_input")
        )
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = {
                selectedAnswers.clear()
                submitted = false
                viewModel.generateQuiz(topic)
            },
            enabled = quizState !is UiState.Loading,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("generate_quiz_button")
        ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Generar quiz de 5 preguntas", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))
        when (val state = quizState) {
            is UiState.Loading -> CircularProgressIndicator()
            is UiState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error)
            is UiState.Success -> {
                val questions = state.data
                questions.forEachIndexed { qIdx, q ->
                    QuizQuestionCard(
                        index = qIdx,
                        question = q,
                        selectedOption = selectedAnswers[qIdx],
                        submitted = submitted,
                        onSelectOption = { optIdx ->
                            if (!submitted) selectedAnswers[qIdx] = optIdx
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                if (!submitted) {
                    Button(
                        onClick = {
                            submitted = true
                            val correct = questions.indices.count { i ->
                                selectedAnswers[i] == questions[i].correctIndex
                            }
                            viewModel.logQuizCompletion(topic, correct, questions.size)
                        },
                        enabled = selectedAnswers.size == questions.size,
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("Calificar respuestas")
                    }
                } else {
                    val correct = questions.indices.count { i ->
                        selectedAnswers[i] == questions[i].correctIndex
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Puntaje final: $correct de ${questions.size} correctas",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }
            null -> Unit
        }
    }
}

@Composable
private fun QuizQuestionCard(
    index: Int,
    question: PracticeQuestion,
    selectedOption: Int?,
    submitted: Boolean,
    onSelectOption: (Int) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "${index + 1}. ${question.question}",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            question.options.forEachIndexed { oIdx, option ->
                val isSelected = selectedOption == oIdx
                val isCorrect = oIdx == question.correctIndex
                val containerColor = when {
                    submitted && isCorrect -> MaterialTheme.colorScheme.tertiaryContainer
                    submitted && isSelected && !isCorrect -> MaterialTheme.colorScheme.errorContainer
                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                }
                Surface(
                    color = containerColor,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clickable { onSelectOption(oIdx) }
                ) {
                    Text(
                        text = option,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }
            if (submitted && question.explanation.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = question.explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun ProgressSection(viewModel: MethodologyViewModel) {
    val sessionsState by viewModel.sessionsState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text("Tu Progreso de Estudio", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "Historial sincronizado de tus sesiones Pomodoro, Feynman, Cornell y Quizzes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(14.dp))

        when (val state = sessionsState) {
            is UiState.Loading -> CircularProgressIndicator()
            is UiState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error)
            is UiState.Success -> {
                val sessions = state.data
                val totalMin = sessions.sumOf { it.durationMinutes }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("$totalMin min", style = MaterialTheme.typography.headlineMedium)
                            Text("Tiempo enfocado", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("${sessions.size}", style = MaterialTheme.typography.headlineMedium)
                            Text("Sesiones totales", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                if (sessions.isEmpty()) {
                    Text(
                        text = "Completa un bloque Pomodoro, evaluación Feynman, apuntes Cornell o Quiz para ver tu historial.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    sessions.forEach { session ->
                        val tech = StudyTechnique.fromName(session.technique)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(tech.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                    Text("${session.scoreOrRating}% • ${session.durationMinutes} min", style = MaterialTheme.typography.labelMedium)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(session.topic, style = MaterialTheme.typography.titleSmall)
                                if (session.summary.isNotBlank()) {
                                    Text(
                                        text = session.summary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
