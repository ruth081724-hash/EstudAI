package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.auth.signOut
import com.example.data.FirestoreRepository
import com.example.data.GeminiAiRepository
import com.example.data.StudyDataRepository
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.HomeworkViewModel
import com.example.ui.viewmodel.MethodologyViewModel
import com.example.voice.VoiceAssistantManager
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

enum class PrimaryDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    CHAT("chat", "Chat IA", Icons.Rounded.AutoAwesome),
    HOMEWORK("homework", "Tareas", Icons.AutoMirrored.Rounded.EventNote),
    STUDY("study", "Estudio", Icons.Rounded.Psychology)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScaffold(
    currentUser: FirebaseUser
) {
    val uid = currentUser.uid
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val voiceManager = remember(context) { VoiceAssistantManager(context) }
    DisposableEffect(voiceManager) {
        onDispose { voiceManager.release() }
    }

    val sharedAiRepo = remember { GeminiAiRepository() }

    val resolveRepository: (android.app.Application) -> StudyDataRepository = { app ->
        val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
        FirestoreRepository(db)
    }

    val chatViewModel: ChatViewModel = viewModel(
        key = "chat_$uid",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                ChatViewModel(resolveRepository(app), sharedAiRepo, uid)
            }
        }
    )

    val homeworkViewModel: HomeworkViewModel = viewModel(
        key = "homework_$uid",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                HomeworkViewModel(resolveRepository(app), sharedAiRepo, uid)
            }
        }
    )

    val methodologyViewModel: MethodologyViewModel = viewModel(
        key = "methodology_$uid",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                MethodologyViewModel(resolveRepository(app), sharedAiRepo, uid)
            }
        }
    )

    var selectedTab by rememberSaveable { mutableStateOf(PrimaryDestination.CHAT) }
    var showAccountSheet by remember { mutableStateOf(false) }

    if (selectedTab != PrimaryDestination.CHAT) {
        BackHandler {
            selectedTab = PrimaryDestination.CHAT
        }
    }

    val showSnackbar: (String) -> Unit = remember(scope, snackbarHostState) {
        { message ->
            scope.launch {
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    ),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "EstudIA",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Text(
                                    text = "Gratis",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showAccountSheet = true },
                            modifier = Modifier.testTag("account_profile_button")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                val initial = currentUser.displayName?.firstOrNull()?.uppercase()
                                    ?: currentUser.email?.firstOrNull()?.uppercase()
                                    ?: "E"
                                Text(
                                    text = initial,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                )
            },
            bottomBar = {
                if (!isWideScreen) {
                    NavigationBar {
                        PrimaryDestination.entries.forEach { dest ->
                            NavigationBarItem(
                                selected = selectedTab == dest,
                                onClick = { selectedTab = dest },
                                icon = {
                                    Icon(imageVector = dest.icon, contentDescription = dest.label)
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag("nav_${dest.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isWideScreen) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))
                        PrimaryDestination.entries.forEach { dest ->
                            NavigationRailItem(
                                selected = selectedTab == dest,
                                onClick = { selectedTab = dest },
                                icon = {
                                    Icon(imageVector = dest.icon, contentDescription = dest.label)
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag("rail_${dest.route}")
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    when (selectedTab) {
                        PrimaryDestination.CHAT -> ChatScreen(
                            viewModel = chatViewModel,
                            voiceManager = voiceManager,
                            onShowSnackbar = showSnackbar
                        )
                        PrimaryDestination.HOMEWORK -> HomeworkScreen(
                            viewModel = homeworkViewModel,
                            onAskAiForTask = { task ->
                                chatViewModel.askAboutHomework(
                                    title = task.title,
                                    subject = task.subject,
                                    notes = task.notes
                                )
                                selectedTab = PrimaryDestination.CHAT
                            },
                            onShowSnackbar = showSnackbar
                        )
                        PrimaryDestination.STUDY -> MethodologyScreen(
                            viewModel = methodologyViewModel,
                            voiceManager = voiceManager,
                            onShowSnackbar = showSnackbar
                        )
                    }
                }
            }
        }
    }

    if (showAccountSheet) {
        ModalBottomSheet(onDismissRequest = { showAccountSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = currentUser.displayName?.takeIf { it.isNotBlank() } ?: "Estudiante",
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (!currentUser.email.isNullOrBlank()) {
                            Text(
                                text = currentUser.email.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Modelos activos (Sin costo)", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• Gemini 3.8 Flash (Explicaciones y fotos)\n• Gemini 3.5 Flash-Lite (Ultra rápido y flashcards)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                OutlinedButton(
                    onClick = {
                        showAccountSheet = false
                        voiceManager.stopSpeaking()
                        voiceManager.stopListening()
                        signOut(credentialManager = credentialManager, scope = scope)
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("sign_out_button")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cerrar sesión")
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}
