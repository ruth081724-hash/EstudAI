package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.auth.AuthScreen
import com.example.auth.attemptAutoSignIn
import com.example.auth.authStateFlow
import com.example.firebase.configureAppCheck
import com.example.notifications.HomeworkReminderScheduler
import com.example.ui.screens.MainAppScaffold
import com.example.ui.theme.EstudIATheme
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent before any UI, ViewModels, or Firebase calls
        configureAppCheck(this, intent)
        HomeworkReminderScheduler.ensureChannel(this)

        enableEdgeToEdge()
        setContent {
            EstudIATheme {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val auth = remember { Firebase.auth }
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    var isCheckingSession by remember { mutableStateOf(auth.currentUser == null) }

    LaunchedEffect(Unit) {
        if (auth.currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = { isCheckingSession = false },
                onUnauthenticated = { isCheckingSession = false },
                scope = scope
            )
        } else {
            isCheckingSession = false
        }
    }

    val user = currentUser
    if (user == null) {
        if (isCheckingSession) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            AuthScreen(
                onAuthSuccess = {
                    isCheckingSession = false
                }
            )
        }
    } else {
        key(user.uid) {
            MainAppScaffold(currentUser = user)
        }
    }
}
