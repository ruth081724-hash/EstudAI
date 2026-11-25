package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.AiModelOption
import com.example.model.ChatMessage
import com.example.model.Conversation
import com.example.model.StudyMode
import com.example.model.UiState
import com.example.ui.viewmodel.ChatViewModel
import com.example.voice.VoiceAssistantManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    voiceManager: VoiceAssistantManager,
    onShowSnackbar: (String) -> Unit
) {
    val context = LocalContext.current
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val selectedMode by viewModel.selectedStudyMode.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val conversationsState by viewModel.conversationsState.collectAsStateWithLifecycle()
    val activeConversationId by viewModel.activeConversationId.collectAsStateWithLifecycle()
    val streamingReply by viewModel.streamingReply.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val voiceAutoReply by viewModel.voiceAutoReply.collectAsStateWithLifecycle()
    val draftInput by viewModel.draftInput.collectAsStateWithLifecycle()
    val attachedBitmap by viewModel.attachedBitmap.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val ttsEvent by viewModel.lastCompletedReplyForTts.collectAsStateWithLifecycle()

    val isListening by voiceManager.isListening.collectAsStateWithLifecycle()
    val partialTranscript by voiceManager.partialTranscript.collectAsStateWithLifecycle()
    val isSpeaking by voiceManager.isSpeaking.collectAsStateWithLifecycle()
    val speakingMessageId by voiceManager.speakingMessageId.collectAsStateWithLifecycle()

    var showModelSheet by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val uiScope = rememberCoroutineScope()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            uiScope.launch {
                val loaded = withContext(Dispatchers.IO) {
                    decodeBitmapFromUri(context, uri)
                }
                if (loaded != null) {
                    viewModel.setAttachedBitmap(loaded)
                } else {
                    onShowSnackbar("No se pudo cargar la imagen seleccionada.")
                }
            }
        }
    }

    val startVoiceCapture = remember(voiceManager, viewModel, onShowSnackbar) {
        {
            voiceManager.startListening(
                onFinalResult = { spokenText ->
                    viewModel.sendMessage(spokenText)
                },
                onError = { err ->
                    onShowSnackbar(err)
                }
            )
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoiceCapture()
        } else {
            onShowSnackbar("Activa el permiso de micrófono para hablar con EstudIA.")
        }
    }

    LaunchedEffect(ttsEvent) {
        val event = ttsEvent
        if (event != null) {
            voiceManager.speak(event.second, messageId = event.first)
            viewModel.consumeTtsEvent()
        }
    }

    LaunchedEffect(errorMessage) {
        val msg = errorMessage
        if (msg != null) {
            onShowSnackbar(msg)
            viewModel.clearError()
        }
    }

    val messages = (messagesState as? UiState.Success)?.data ?: emptyList()
    LaunchedEffect(messages.size, streamingReply) {
        val total = messages.size + if (streamingReply != null) 1 else 0
        if (total > 0) {
            listState.animateScrollToItem(total - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        // Top toolbar inside Chat tab: History, Model Switcher, Voice Mode, New Chat
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = { showHistorySheet = true },
                        modifier = Modifier.testTag("chat_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = "Historial de chats"
                        )
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier
                            .clickable { showModelSheet = true }
                            .testTag("model_selector_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = selectedModel.displayName,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "Cambiar modelo o método",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = voiceAutoReply,
                        onClick = {
                            if (voiceAutoReply && isSpeaking) voiceManager.stopSpeaking()
                            viewModel.toggleVoiceAutoReply()
                        },
                        label = { Text("Voz", style = MaterialTheme.typography.labelMedium) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.RecordVoiceOver,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("voice_mode_chip")
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = {
                            voiceManager.stopSpeaking()
                            viewModel.startNewChat()
                        },
                        modifier = Modifier.testTag("new_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Nuevo chat"
                        )
                    }
                }
            }
        }

        // Study mode subtle strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StudyMode.entries.forEach { mode ->
                FilterChip(
                    selected = selectedMode == mode,
                    onClick = { viewModel.selectStudyMode(mode) },
                    label = { Text(mode.label) },
                    modifier = Modifier.testTag("study_mode_${mode.name.lowercase()}")
                )
            }
        }

        // Chat message list or Gemini-style welcome canvas
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (messagesState is UiState.Error) {
                val err = (messagesState as UiState.Error).message
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else if (messages.isEmpty() && streamingReply == null) {
                ChatEmptyState(
                    selectedModel = selectedModel,
                    selectedMode = selectedMode,
                    onPromptClick = { viewModel.sendMessage(it) },
                    onOpenModelSheet = { showModelSheet = true }
                )
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(messages, key = { idx, msg -> msg.id.ifEmpty { "pending_$idx" } }) { _, msg ->
                        ChatMessageBubble(
                            message = msg,
                            isCurrentlySpeaking = isSpeaking && speakingMessageId == msg.id,
                            onToggleSpeak = {
                                if (isSpeaking && speakingMessageId == msg.id) {
                                    voiceManager.stopSpeaking()
                                } else {
                                    voiceManager.speak(msg.text, messageId = msg.id)
                                }
                            }
                        )
                    }

                    if (streamingReply != null) {
                        item(key = "streaming_bubble") {
                            StreamingAssistantBubble(
                                text = streamingReply.orEmpty(),
                                modelName = selectedModel.displayName
                            )
                        }
                    }
                }
            }
        }

        // Active Voice Listening banner
        AnimatedVisibility(visible = isListening) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Mic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = partialTranscript.ifBlank { "Escuchando tu pregunta en español..." },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextButton(onClick = { voiceManager.stopListening() }) {
                        Text("Detener")
                    }
                }
            }
        }

        // Active Voice Speaking banner
        AnimatedVisibility(visible = isSpeaking && !isListening) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Leyendo respuesta en voz alta...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    TextButton(onClick = { voiceManager.stopSpeaking() }) {
                        Text("Detener")
                    }
                }
            }
        }

        // Attached homework image preview
        AnimatedVisibility(visible = attachedBitmap != null) {
            val bmp = attachedBitmap
            if (bmp != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Foto de tarea adjunta",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = CircleShape,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(28.dp)
                                .clickable { viewModel.setAttachedBitmap(null) }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Quitar foto",
                                modifier = Modifier.padding(5.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Foto de tarea lista para analizar con ${selectedModel.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Bottom composer bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                IconButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.testTag("attach_photo_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AddPhotoAlternate,
                        contentDescription = "Adjuntar foto de tarea"
                    )
                }

                IconButton(
                    onClick = {
                        if (isListening) {
                            voiceManager.stopListening()
                        } else {
                            val hasMic = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasMic) {
                                startVoiceCapture()
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    },
                    colors = if (isListening) {
                        IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    } else {
                        IconButtonDefaults.iconButtonColors()
                    },
                    modifier = Modifier.testTag("voice_input_button")
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                        contentDescription = if (isListening) "Detener dictado" else "Hablar con EstudIA"
                    )
                }

                OutlinedTextField(
                    value = draftInput,
                    onValueChange = { viewModel.setDraftInput(it) },
                    placeholder = {
                        Text("Pregunta sobre tu tarea o tema...")
                    },
                    maxLines = 4,
                    shape = RoundedCornerShape(22.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_prompt_input")
                )

                Spacer(modifier = Modifier.width(6.dp))

                FilledIconButton(
                    onClick = { viewModel.sendMessage() },
                    enabled = !isGenerating && (draftInput.isNotBlank() || attachedBitmap != null),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("send_message_button")
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Enviar mensaje"
                        )
                    }
                }
            }
        }
    }

    if (showModelSheet) {
        ModalBottomSheet(onDismissRequest = { showModelSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Modelos Gemini Gratis",
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Cambia de modelo según el tipo de tarea o velocidad que necesites.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))

                AiModelOption.entries.forEach { option ->
                    val isSelected = selectedModel == option
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            }
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .clickable {
                                viewModel.selectModel(option)
                                showModelSheet = false
                            }
                            .testTag("model_option_${option.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    viewModel.selectModel(option)
                                    showModelSheet = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = option.displayName,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Surface(
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                        shape = RoundedCornerShape(999.dp)
                                    ) {
                                        Text(
                                            text = option.badge,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = option.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    if (showHistorySheet) {
        ModalBottomSheet(onDismissRequest = { showHistorySheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Historial de Estudio",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    TextButton(
                        onClick = {
                            viewModel.startNewChat()
                            showHistorySheet = false
                        }
                    ) {
                        Text("+ Nuevo chat")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                when (val state = conversationsState) {
                    is UiState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    is UiState.Error -> {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    is UiState.Success -> {
                        if (state.data.isEmpty()) {
                            Text(
                                text = "Aún no tienes chats guardados. Envía tu primera pregunta.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 20.dp)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.height(340.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(state.data, key = { it.id }) { conv ->
                                    ConversationHistoryRow(
                                        conversation = conv,
                                        isActive = conv.id == activeConversationId,
                                        onSelect = {
                                            viewModel.openConversation(conv)
                                            showHistorySheet = false
                                        },
                                        onDelete = { viewModel.deleteConversation(conv.id) }
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ChatEmptyState(
    selectedModel: AiModelOption,
    selectedMode: StudyMode,
    onPromptClick: (String) -> Unit,
    onOpenModelSheet: () -> Unit
) {
    val starterPrompts = listOf(
        "Explícame paso a paso cómo resolver ecuaciones cuadráticas",
        "Enséñame la fotosíntesis usando el método Feynman",
        "Crea un plan de estudio de 3 días para mi examen de Historia",
        "Dame 5 consejos prácticos para no procrastinar con mis tareas"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_tutor_avatar),
            contentDescription = "Avatar de EstudIA",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "¿Qué estudiamos hoy?",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "${selectedModel.displayName} • ${selectedMode.label}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { onOpenModelSheet() }
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Escribe, dicta con el micrófono o sube una foto de tu tarea.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.widthIn(max = 520.dp)
        ) {
            starterPrompts.forEach { prompt ->
                AssistChip(
                    onClick = { onPromptClick(prompt) },
                    label = {
                        Text(
                            text = prompt,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(
    message: ChatMessage,
    isCurrentlySpeaking: Boolean,
    onToggleSpeak: () -> Unit
) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Image(
                painter = painterResource(id = R.drawable.img_tutor_avatar),
                contentDescription = "EstudIA",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .padding(top = 4.dp, end = 8.dp)
                    .size(32.dp)
                    .clip(CircleShape)
            )
        }

        Surface(
            color = if (isUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
            tonalElevation = if (isUser) 0.dp else 2.dp,
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            modifier = Modifier.widthIn(max = 540.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (isUser && message.hasImage) {
                    Text(
                        text = "[Foto de tarea adjunta]",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isUser) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                if (!isUser) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = AiModelOption.fromModelId(message.modelUsed).displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickable { onToggleSpeak() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (isCurrentlySpeaking) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.VolumeUp,
                                contentDescription = if (isCurrentlySpeaking) "Detener lectura" else "Escuchar respuesta",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isCurrentlySpeaking) "Detener" else "Escuchar",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamingAssistantBubble(
    text: String,
    modelName: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_tutor_avatar),
            contentDescription = "EstudIA escribiendo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .padding(top = 4.dp, end = 8.dp)
                .size(32.dp)
                .clip(CircleShape)
        )
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
            modifier = Modifier.widthIn(max = 540.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = text.ifBlank { "Pensando con $modelName..." },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun ConversationHistoryRow(
    conversation: Conversation,
    isActive: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            }
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (conversation.lastMessagePreview.isNotBlank()) {
                    Text(
                        text = conversation.lastMessagePreview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Eliminar conversación"
                )
            }
        }
    }
}

private fun decodeBitmapFromUri(context: android.content.Context, uri: Uri): Bitmap? {
    val maxDim = 1280
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val w = info.size.width
                val h = info.size.height
                if (w > maxDim || h > maxDim) {
                    val ratio = minOf(maxDim.toFloat() / w, maxDim.toFloat() / h)
                    decoder.setTargetSize(
                        (w * ratio).toInt().coerceAtLeast(1),
                        (h * ratio).toInt().coerceAtLeast(1)
                    )
                }
            }
        } else {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }
            var sampleSize = 1
            while (boundsOpts.outWidth / sampleSize > maxDim || boundsOpts.outHeight / sampleSize > maxDim) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            }
        }
    } catch (_: Exception) {
        null
    }
}
