package com.theglinky.githubremote

import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

object AppTheme {
    val DarkBg = Color(0xFF0A0E27)
    val Cyan = Color(0xFF00D9FF)
    val Purple = Color(0xFF9D4EDD)
    val Pink = Color(0xFFFF006E)
    val CardBg = Color(0xFF1A1F3A)
}

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: GitHubViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this).get(GitHubViewModel::class.java)
        viewModel.init(applicationContext)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize().background(AppTheme.DarkBg),
                    color = AppTheme.DarkBg
                ) {
                    GitHubRemoteApp(viewModel, this)
                }
            }
        }
    }
}

sealed class Screen {
    object Login : Screen()
    object FileBrowser : Screen()
    object FileEditor : Screen()
    object Actions : Screen()
}

@Composable
fun GitHubRemoteApp(viewModel: GitHubViewModel, activity: ComponentActivity) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Login) }
    var selectedFilePath by remember { mutableStateOf("") }
    val hasToken by viewModel.hasToken.collectAsState()

    LaunchedEffect(hasToken) {
        if (hasToken && currentScreen == Screen.Login) {
            currentScreen = Screen.FileBrowser
            viewModel.loadFiles()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(AppTheme.DarkBg)) {
        if (hasToken && currentScreen != Screen.Login) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppTheme.CardBg)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row {
                    TabButton("Dateien", currentScreen == Screen.FileBrowser) {
                        currentScreen = Screen.FileBrowser
                        viewModel.loadFiles()
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TabButton("Actions", currentScreen == Screen.Actions) {
                        currentScreen = Screen.Actions
                        viewModel.loadWorkflowRuns()
                    }
                }
                TextButton(onClick = { viewModel.logout(); currentScreen = Screen.Login }) {
                    Text("Logout", color = AppTheme.Pink, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }

        when (currentScreen) {
            Screen.Login -> LoginScreen(viewModel)
            Screen.FileBrowser -> FileBrowserScreen(viewModel) { path ->
                selectedFilePath = path
                currentScreen = Screen.FileEditor
                viewModel.loadFileContent(path)
            }
            Screen.FileEditor -> FileEditorScreen(viewModel, selectedFilePath) {
                currentScreen = Screen.FileBrowser
            }
            Screen.Actions -> ActionsScreen(viewModel, activity)
        }
    }
}

@Composable
fun TabButton(text: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) AppTheme.Purple else Color(0xFF2A3050)
        ),
        modifier = Modifier.height(36.dp)
    ) {
        Text(text, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun LoginScreen(viewModel: GitHubViewModel) {
    var token by remember { mutableStateOf("") }
    var owner by remember { mutableStateOf("") }
    var repo by remember { mutableStateOf("") }
    var tokenVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "GITHUB REMOTE",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = AppTheme.Cyan,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            "Personal Access Token noetig: github.com -> Settings -> Developer settings -> Personal access tokens -> Generate new token (classic) -> Haken bei 'repo' und 'workflow' setzen",
            fontSize = 11.sp,
            color = Color.Gray,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        OutlinedTextField(
            value = owner,
            onValueChange = { owner = it },
            label = { Text("GitHub Benutzername", color = AppTheme.Cyan) },
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AppTheme.Cyan,
                unfocusedBorderColor = AppTheme.Purple,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        OutlinedTextField(
            value = repo,
            onValueChange = { repo = it },
            label = { Text("Repository Name", color = AppTheme.Cyan) },
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AppTheme.Cyan,
                unfocusedBorderColor = AppTheme.Purple,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Personal Access Token", color = AppTheme.Cyan) },
            visualTransformation = if (tokenVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { tokenVisible = !tokenVisible }) {
                    Text(if (tokenVisible) "Verbergen" else "Anzeigen", color = AppTheme.Cyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            },
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AppTheme.Cyan,
                unfocusedBorderColor = AppTheme.Purple,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        Button(
            onClick = { viewModel.saveCredentials(token, owner, repo) },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppTheme.Purple),
            enabled = token.isNotEmpty() && owner.isNotEmpty() && repo.isNotEmpty()
        ) {
            Text("VERBINDEN", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        }
    }
}
