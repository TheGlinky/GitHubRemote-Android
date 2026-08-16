package com.theglinky.githubremote

/**
 * Enthaelt alle Grundgeruest-Dateien fuer ein neues Android-App-Projekt.
 * Platzhalter appname/AppName werden beim Erstellen ersetzt.
 */
object ProjectTemplates {

    data class TemplateFile(val path: String, val content: String)

    fun getAllFiles(packageSuffix: String, appNameCapitalized: String, displayName: String): List<TemplateFile> {
        val pkg = "com.theglinky.$packageSuffix"

        return listOf(
            TemplateFile(
                path = "build.gradle.kts",
                content = """
plugins {
    id("com.android.application") version "8.7.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}
""".trimIndent()
            ),
            TemplateFile(
                path = "settings.gradle.kts",
                content = """
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "$appNameCapitalized"
include(":app")
""".trimIndent()
            ),
            TemplateFile(
                path = "gradle.properties",
                content = """
android.useAndroidX=true
android.enableJetifier=false
kotlin.code.style=official
""".trimIndent()
            ),
            TemplateFile(
                path = ".github/workflows/build-apk.yml",
                content = """
name: Build APK

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
    - uses: actions/checkout@v4

    - name: Set up JDK 17
      uses: actions/setup-java@v4
      with:
        java-version: '17'
        distribution: 'temurin'
        cache: gradle

    - name: Build Debug APK with Gradle
      run: |
        gradle --version
        gradle assembleDebug

    - name: Upload Debug APK
      uses: actions/upload-artifact@v4
      with:
        name: debug-apk
        path: app/build/outputs/apk/debug/app-debug.apk
        retention-days: 30
""".trimIndent()
            ),
            TemplateFile(
                path = "app/build.gradle.kts",
                content = """
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    compileSdk = 34
    namespace = "$pkg"

    defaultConfig {
        applicationId = "$pkg"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.compose.ui:ui:1.6.1")
    implementation("androidx.compose.material3:material3:1.1.1")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.1")
    debugImplementation("androidx.compose.ui:ui-tooling:1.6.1")

    implementation("androidx.activity:activity-compose:1.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")

    implementation("com.squareup.okhttp3:okhttp:4.11.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
""".trimIndent()
            ),
            TemplateFile(
                path = "app/src/main/AndroidManifest.xml",
                content = """
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <application
        android:allowBackup="true"
        android:label="@string/app_name"
        android:usesCleartextTraffic="true"
        android:theme="@style/Theme.$appNameCapitalized">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.$appNameCapitalized">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

    </application>

</manifest>
""".trimIndent()
            ),
            TemplateFile(
                path = "app/src/main/res/values/strings.xml",
                content = """
<resources>
    <string name="app_name">$displayName</string>
</resources>
""".trimIndent()
            ),
            TemplateFile(
                path = "app/src/main/res/values/themes.xml",
                content = """
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.$appNameCapitalized" parent="android:Theme.Material.NoActionBar">
        <item name="android:windowBackground">@color/dark_bg</item>
        <item name="android:statusBarColor">#000000</item>
        <item name="android:navigationBarColor">#0A0E27</item>
    </style>
    <color name="dark_bg">#0A0E27</color>
</resources>
""".trimIndent()
            ),
            TemplateFile(
                path = "app/src/main/java/${pkg.replace(".", "/")}/MainActivity.kt",
                content = """
package $pkg

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider

object AppTheme {
    val DarkBg = Color(0xFF0A0E27)
    val Cyan = Color(0xFF00D9FF)
    val Purple = Color(0xFF9D4EDD)
    val Pink = Color(0xFFFF006E)
    val CardBg = Color(0xFF1A1F3A)
}

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: AppViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this).get(AppViewModel::class.java)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize().background(AppTheme.DarkBg),
                    color = AppTheme.DarkBg
                ) {
                    AppRoot(viewModel)
                }
            }
        }
    }
}

@Composable
fun AppRoot(viewModel: AppViewModel) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "$displayName\nBereit zum Entwickeln",
            color = AppTheme.Cyan,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
""".trimIndent()
            ),
            TemplateFile(
                path = "app/src/main/java/${pkg.replace(".", "/")}/AppViewModel.kt",
                content = """
package $pkg

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class LogLevel { INFO, SUCCESS, ERROR, WARNING }
data class LogEntry(val timestamp: String, val message: String, val level: LogLevel)

class AppViewModel : ViewModel() {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private var prefs: SharedPreferences? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private fun timestamp(): String =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

    private fun log(message: String, level: LogLevel = LogLevel.INFO) {
        _logs.value = _logs.value + LogEntry(timestamp(), message, level)
        when (level) {
            LogLevel.ERROR -> Log.e("AppViewModel", message)
            else -> Log.d("AppViewModel", message)
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun init(context: Context) {
        prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        log("App initialisiert", LogLevel.INFO)
    }
}
""".trimIndent()
            )
        )
    }
}
