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
