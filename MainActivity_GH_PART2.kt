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
