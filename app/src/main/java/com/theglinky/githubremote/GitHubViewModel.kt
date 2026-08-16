package com.theglinky.githubremote

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class RepoFile(
    val name: String,
    val path: String,
    val type: String, // "file" oder "dir"
    val sha: String = ""
)

data class WorkflowRun(
    val id: Long,
    val status: String,
    val conclusion: String?,
    val name: String,
    val htmlUrl: String
)

data class WorkflowArtifact(
    val id: Long,
    val name: String,
    val downloadUrl: String
)

enum class LogLevel { INFO, SUCCESS, ERROR, WARNING }
data class LogEntry(val timestamp: String, val message: String, val level: LogLevel)

class GitHubViewModel : ViewModel() {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs

    private val _hasToken = MutableStateFlow(false)
    val hasToken: StateFlow<Boolean> = _hasToken

    private val _files = MutableStateFlow<List<RepoFile>>(emptyList())
    val files: StateFlow<List<RepoFile>> = _files

    private val _currentPath = MutableStateFlow("")
    val currentPath: StateFlow<String> = _currentPath

    private val _fileContent = MutableStateFlow("")
    val fileContent: StateFlow<String> = _fileContent

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _workflowRuns = MutableStateFlow<List<WorkflowRun>>(emptyList())
    val workflowRuns: StateFlow<List<WorkflowRun>> = _workflowRuns

    private val _artifacts = MutableStateFlow<List<WorkflowArtifact>>(emptyList())
    val artifacts: StateFlow<List<WorkflowArtifact>> = _artifacts

    private var token = ""
    private var owner = ""
    private var repo = ""
    private var currentFileSha = ""
    private var prefs: SharedPreferences? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun timestamp(): String =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

    private fun log(message: String, level: LogLevel = LogLevel.INFO) {
        _logs.value = _logs.value + LogEntry(timestamp(), message, level)
        when (level) {
            LogLevel.ERROR -> Log.e("GitHubViewModel", message)
            else -> Log.d("GitHubViewModel", message)
        }
    }

    fun init(context: Context) {
        prefs = context.getSharedPreferences("github_prefs", Context.MODE_PRIVATE)
        val savedToken = prefs?.getString("token", "") ?: ""
        val savedOwner = prefs?.getString("owner", "") ?: ""
        val savedRepo = prefs?.getString("repo", "") ?: ""

        if (savedToken.isNotEmpty()) {
            token = savedToken
            owner = savedOwner
            repo = savedRepo
            _hasToken.value = true
        }
    }

    fun saveCredentials(newToken: String, newOwner: String, newRepo: String) {
        token = newToken.trim()
        owner = newOwner.trim()
        repo = newRepo.trim()
        prefs?.edit()
            ?.putString("token", token)
            ?.putString("owner", owner)
            ?.putString("repo", repo)
            ?.apply()
        _hasToken.value = true
        log("Zugangsdaten gespeichert fuer $owner/$repo", LogLevel.SUCCESS)
    }

    fun logout() {
        prefs?.edit()?.clear()?.apply()
        token = ""; owner = ""; repo = ""
        _hasToken.value = false
        _files.value = emptyList()
        log("Abgemeldet", LogLevel.WARNING)
    }

    private fun authHeader() = "Bearer $token"

    fun loadFiles(path: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.emit(true)
            try {
                val url = "https://api.github.com/repos/$owner/$repo/contents/$path"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", authHeader())
                    .addHeader("Accept", "application/vnd.github+json")
                    .build()

                val response = httpClient.newCall(request).execute()
                val body = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    throw Exception("HTTP ${response.code}: ${extractErrorMessage(body)}")
                }

                val jsonArray = JSONArray(body)
                val fileList = mutableListOf<RepoFile>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    fileList.add(
                        RepoFile(
                            name = obj.getString("name"),
                            path = obj.getString("path"),
                            type = obj.getString("type"),
                            sha = obj.optString("sha", "")
                        )
                    )
                }
                fileList.sortWith(compareBy({ it.type != "dir" }, { it.name.lowercase() }))

                _files.emit(fileList)
                _currentPath.emit(path)
                log("${fileList.size} Eintraege geladen: ${if (path.isEmpty()) "/" else path}", LogLevel.SUCCESS)
            } catch (e: Exception) {
                log("Fehler beim Laden: ${e.message ?: e.javaClass.simpleName}", LogLevel.ERROR)
            } finally {
                _isLoading.emit(false)
            }
        }
    }

    fun loadFileContent(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.emit(true)
            try {
                val url = "https://api.github.com/repos/$owner/$repo/contents/$path"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", authHeader())
                    .addHeader("Accept", "application/vnd.github+json")
                    .build()

                val response = httpClient.newCall(request).execute()
                val body = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    throw Exception("HTTP ${response.code}: ${extractErrorMessage(body)}")
                }

                val obj = JSONObject(body)
                currentFileSha = obj.getString("sha")
                val base64Content = obj.getString("content").replace("\n", "")
                val decoded = String(Base64.decode(base64Content, Base64.DEFAULT), Charsets.UTF_8)

                _fileContent.emit(decoded)
                log("Datei geladen: $path", LogLevel.SUCCESS)
            } catch (e: Exception) {
                log("Fehler beim Laden der Datei: ${e.message ?: e.javaClass.simpleName}", LogLevel.ERROR)
            } finally {
                _isLoading.emit(false)
            }
        }
    }

    fun saveFileContent(path: String, content: String, commitMessage: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.emit(true)
            try {
                val url = "https://api.github.com/repos/$owner/$repo/contents/$path"
                val encodedContent = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

                val json = JSONObject().apply {
                    put("message", commitMessage.ifEmpty { "Update $path" })
                    put("content", encodedContent)
                    if (currentFileSha.isNotEmpty()) {
                        put("sha", currentFileSha)
                    }
                }

                val mediaType = "application/json".toMediaType()
                val requestBody = json.toString().toRequestBody(mediaType)

                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", authHeader())
                    .addHeader("Accept", "application/vnd.github+json")
                    .put(requestBody)
                    .build()

                val response = httpClient.newCall(request).execute()
                val body = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    throw Exception("HTTP ${response.code}: ${extractErrorMessage(body)}")
                }

                log("Gespeichert: $path", LogLevel.SUCCESS)
            } catch (e: Exception) {
                log("Fehler beim Speichern: ${e.message ?: e.javaClass.simpleName}", LogLevel.ERROR)
            } finally {
                _isLoading.emit(false)
            }
        }
    }

    fun createNewFile(path: String, content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.emit(true)
            try {
                val url = "https://api.github.com/repos/$owner/$repo/contents/$path"
                val encodedContent = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

                val json = JSONObject().apply {
                    put("message", "Create $path")
                    put("content", encodedContent)
                }

                val mediaType = "application/json".toMediaType()
                val requestBody = json.toString().toRequestBody(mediaType)

                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", authHeader())
                    .addHeader("Accept", "application/vnd.github+json")
                    .put(requestBody)
                    .build()

                val response = httpClient.newCall(request).execute()
                val body = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    throw Exception("HTTP ${response.code}: ${extractErrorMessage(body)}")
                }

                log("Neue Datei erstellt: $path", LogLevel.SUCCESS)
            } catch (e: Exception) {
                log("Fehler beim Erstellen: ${e.message ?: e.javaClass.simpleName}", LogLevel.ERROR)
            } finally {
                _isLoading.emit(false)
            }
        }
    }

    fun triggerWorkflow(workflowFileName: String, branch: String = "main") {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.emit(true)
            try {
                val url = "https://api.github.com/repos/$owner/$repo/actions/workflows/$workflowFileName/dispatches"

                val json = JSONObject().apply {
                    put("ref", branch)
                }

                val mediaType = "application/json".toMediaType()
                val requestBody = json.toString().toRequestBody(mediaType)

                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", authHeader())
                    .addHeader("Accept", "application/vnd.github+json")
                    .post(requestBody)
                    .build()

                val response = httpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    throw Exception("HTTP ${response.code}: ${extractErrorMessage(body)}")
                }

                log("Workflow gestartet: $workflowFileName", LogLevel.SUCCESS)
            } catch (e: Exception) {
                log("Fehler beim Starten: ${e.message ?: e.javaClass.simpleName}", LogLevel.ERROR)
            } finally {
                _isLoading.emit(false)
            }
        }
    }

    fun loadWorkflowRuns() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = "https://api.github.com/repos/$owner/$repo/actions/runs?per_page=10"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", authHeader())
                    .addHeader("Accept", "application/vnd.github+json")
                    .build()

                val response = httpClient.newCall(request).execute()
                val body = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    throw Exception("HTTP ${response.code}: ${extractErrorMessage(body)}")
                }

                val obj = JSONObject(body)
                val runsArray = obj.getJSONArray("workflow_runs")
                val runsList = mutableListOf<WorkflowRun>()

                for (i in 0 until runsArray.length()) {
                    val runObj = runsArray.getJSONObject(i)
                    runsList.add(
                        WorkflowRun(
                            id = runObj.getLong("id"),
                            status = runObj.getString("status"),
                            conclusion = runObj.optString("conclusion", null),
                            name = runObj.optString("name", "Workflow"),
                            htmlUrl = runObj.getString("html_url")
                        )
                    )
                }

                _workflowRuns.emit(runsList)
            } catch (e: Exception) {
                log("Fehler beim Laden der Workflow-Runs: ${e.message ?: e.javaClass.simpleName}", LogLevel.ERROR)
            }
        }
    }

    fun loadArtifacts(runId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = "https://api.github.com/repos/$owner/$repo/actions/runs/$runId/artifacts"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", authHeader())
                    .addHeader("Accept", "application/vnd.github+json")
                    .build()

                val response = httpClient.newCall(request).execute()
                val body = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    throw Exception("HTTP ${response.code}: ${extractErrorMessage(body)}")
                }

                val obj = JSONObject(body)
                val artifactsArray = obj.getJSONArray("artifacts")
                val artifactsList = mutableListOf<WorkflowArtifact>()

                for (i in 0 until artifactsArray.length()) {
                    val artObj = artifactsArray.getJSONObject(i)
                    artifactsList.add(
                        WorkflowArtifact(
                            id = artObj.getLong("id"),
                            name = artObj.getString("name"),
                            downloadUrl = artObj.getString("archive_download_url")
                        )
                    )
                }

                _artifacts.emit(artifactsList)
                log("${artifactsList.size} Artefakt(e) gefunden", LogLevel.SUCCESS)
            } catch (e: Exception) {
                log("Fehler beim Laden der Artefakte: ${e.message ?: e.javaClass.simpleName}", LogLevel.ERROR)
            }
        }
    }

    suspend fun downloadArtifact(downloadUrl: String): ByteArray? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(downloadUrl)
                    .addHeader("Authorization", authHeader())
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    log("Download fehlgeschlagen: HTTP ${response.code}", LogLevel.ERROR)
                    return@withContext null
                }

                response.body?.bytes()
            } catch (e: Exception) {
                log("Fehler beim Download: ${e.message ?: e.javaClass.simpleName}", LogLevel.ERROR)
                null
            }
        }
    }

    private fun extractErrorMessage(body: String): String {
        return try {
            JSONObject(body).optString("message", "Unbekannter Fehler")
        } catch (e: Exception) {
            "Unbekannter Fehler"
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
}
