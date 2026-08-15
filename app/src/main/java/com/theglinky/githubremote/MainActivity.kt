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
@Composable
fun FileBrowserScreen(viewModel: GitHubViewModel, onFileClick: (String) -> Unit) {
    val files by viewModel.files.collectAsState()
    val currentPath by viewModel.currentPath.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var showNewFileDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (currentPath.isEmpty()) "/" else "/$currentPath",
                color = AppTheme.Cyan,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { showNewFileDialog = true }) {
                Text("+ Neu", color = AppTheme.Purple, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        }

        if (currentPath.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val parent = currentPath.substringBeforeLast("/", "")
                        viewModel.loadFiles(parent)
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(".. (zurueck)", color = Color.Gray, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppTheme.Cyan)
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(files) { file ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (file.type == "dir") {
                                viewModel.loadFiles(file.path)
                            } else {
                                onFileClick(file.path)
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (file.type == "dir") "[DIR]" else "[FILE]",
                        color = if (file.type == "dir") AppTheme.Purple else AppTheme.Cyan,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(60.dp)
                    )
                    Text(
                        file.name,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Divider(color = Color(0xFF2A3050))
            }
        }
    }

    if (showNewFileDialog) {
        NewFileDialog(
            currentPath = currentPath,
            onDismiss = { showNewFileDialog = false },
            onCreate = { fileName, content ->
                val fullPath = if (currentPath.isEmpty()) fileName else "$currentPath/$fileName"
                viewModel.createNewFile(fullPath, content)
                showNewFileDialog = false
            }
        )
    }
}

@Composable
fun NewFileDialog(currentPath: String, onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var fileName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Neue Datei", color = AppTheme.Cyan, fontFamily = FontFamily.Monospace) },
        text = {
            Column {
                Text(
                    "In: ${if (currentPath.isEmpty()) "/" else "/$currentPath"}",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("Dateiname", color = AppTheme.Cyan) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = AppTheme.Cyan
                    )
                )
            }
        },
        containerColor = AppTheme.CardBg,
        confirmButton = {
            Button(
                onClick = { if (fileName.isNotEmpty()) onCreate(fileName, "") },
                colors = ButtonDefaults.buttonColors(containerColor = AppTheme.Purple)
            ) {
                Text("ERSTELLEN", fontFamily = FontFamily.Monospace)
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A3050))
            ) {
                Text("ABBRECHEN", fontFamily = FontFamily.Monospace)
            }
        }
    )
}

@Composable
fun FileEditorScreen(viewModel: GitHubViewModel, path: String, onBack: () -> Unit) {
    val fileContent by viewModel.fileContent.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var editedContent by remember { mutableStateOf("") }
    var hasLoadedOnce by remember { mutableStateOf(false) }

    LaunchedEffect(fileContent) {
        if (!hasLoadedOnce) {
            editedContent = fileContent
            hasLoadedOnce = true
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("< Zurueck", color = AppTheme.Cyan, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
            Text(
                path.substringAfterLast("/"),
                color = Color.White,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { viewModel.saveFileContent(path, editedContent, "Update ${path.substringAfterLast("/")}") },
                colors = ButtonDefaults.buttonColors(containerColor = AppTheme.Purple),
                enabled = !isLoading
            ) {
                Text("SPEICHERN", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }

        if (isLoading && !hasLoadedOnce) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppTheme.Cyan)
            }
        } else {
            OutlinedTextField(
                value = editedContent,
                onValueChange = { editedContent = it },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color.White
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppTheme.Cyan,
                    unfocusedBorderColor = AppTheme.Purple,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
        }
    }
}

@Composable
fun ActionsScreen(viewModel: GitHubViewModel, activity: ComponentActivity) {
    val logs by viewModel.logs.collectAsState()
    val workflowRuns by viewModel.workflowRuns.collectAsState()
    val artifacts by viewModel.artifacts.collectAsState()
    var workflowFileName by remember { mutableStateOf("build-apk.yml") }
    var selectedRunId by remember { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()
    var downloadStatus by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "GITHUB ACTIONS",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = AppTheme.Cyan,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        OutlinedTextField(
            value = workflowFileName,
            onValueChange = { workflowFileName = it },
            label = { Text("Workflow-Datei", color = AppTheme.Cyan) },
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AppTheme.Cyan,
                unfocusedBorderColor = AppTheme.Purple,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { viewModel.triggerWorkflow(workflowFileName) },
                colors = ButtonDefaults.buttonColors(containerColor = AppTheme.Purple),
                modifier = Modifier.weight(1f)
            ) {
                Text("BUILD STARTEN", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { viewModel.loadWorkflowRuns() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A3050))
            ) {
                Text("AKTUALISIEREN", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            "Letzte Laeufe",
            color = Color.Gray,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        workflowRuns.forEach { run ->
            val statusColor = when {
                run.status == "in_progress" || run.status == "queued" -> Color(0xFFFFC107)
                run.conclusion == "success" -> Color(0xFF00FF66)
                run.conclusion == "failure" -> Color(0xFFFF3355)
                else -> Color.Gray
            }
            val statusText = when {
                run.status == "in_progress" -> "laeuft..."
                run.status == "queued" -> "wartet..."
                run.conclusion == "success" -> "erfolgreich"
                run.conclusion == "failure" -> "fehlgeschlagen"
                else -> run.status
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clickable {
                        selectedRunId = run.id
                        viewModel.loadArtifacts(run.id)
                    },
                colors = CardDefaults.cardColors(containerColor = AppTheme.CardBg)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(run.name, color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    Text(statusText, color = statusColor, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }

            if (selectedRunId == run.id && artifacts.isNotEmpty()) {
                artifacts.forEach { artifact ->
                    Button(
                        onClick = {
                            scope.launch {
                                downloadStatus = "Lade herunter..."
                                val bytes = viewModel.downloadArtifact(artifact.downloadUrl)
                                if (bytes != null) {
                                    val apkPath = extractApkFromZip(bytes, activity)
                                    downloadStatus = if (apkPath != null) {
                                        "Gespeichert: $apkPath"
                                    } else {
                                        "Keine APK im Artefakt gefunden"
                                    }
                                } else {
                                    downloadStatus = "Download fehlgeschlagen"
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AppTheme.Pink),
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, bottom = 8.dp)
                    ) {
                        Text("APK LADEN: ${artifact.name}", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        if (downloadStatus.isNotEmpty()) {
            Text(
                downloadStatus,
                color = AppTheme.Cyan,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text("Log", color = Color.Gray, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(bottom = 6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(Color(0xFF050810), RoundedCornerShape(8.dp))
                .padding(10.dp)
        ) {
            LazyColumn {
                items(logs.takeLast(30)) { entry ->
                    val color = when (entry.level) {
                        LogLevel.SUCCESS -> Color(0xFF00FF66)
                        LogLevel.ERROR -> Color(0xFFFF3355)
                        LogLevel.WARNING -> Color(0xFFFFC107)
                        LogLevel.INFO -> Color(0xFF888888)
                    }
                    Text(
                        "${entry.timestamp}  ${entry.message}",
                        color = color,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 13.sp
                    )
                }
            }
        }
    }
}

/**
 * GitHub Artifacts kommen als ZIP. Wir entpacken die erste .apk Datei darin
 * und speichern sie im Downloads-Ordner.
 */
fun extractApkFromZip(zipBytes: ByteArray, activity: ComponentActivity): String? {
    try {
        val zipStream = ZipInputStream(zipBytes.inputStream())
        var entry = zipStream.nextEntry

        while (entry != null) {
            if (entry.name.endsWith(".apk")) {
                val downloadsDir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                val outFile = File(downloadsDir, entry.name)
                FileOutputStream(outFile).use { output ->
                    zipStream.copyTo(output)
                }
                return outFile.absolutePath
            }
            entry = zipStream.nextEntry
        }
        return null
    } catch (e: Exception) {
        return null
    }
}

