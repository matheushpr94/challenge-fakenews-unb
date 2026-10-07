import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Configuração local por máquina (não versionada): local.properties na raiz do projeto.
//   lume.ollama.url=http://127.0.0.1:11434   (emulador/aparelho via `adb reverse tcp:11434 tcp:11434`)
//   lume.ollama.embedModel=paraphrase-multilingual
//   lume.roleclassifier.url=http://127.0.0.1:8765   (opcional; vazio = análise de linguagem desligada; `adb reverse tcp:8765 tcp:8765`)
val localProps = Properties().apply {
    providers.fileContents(rootProject.layout.projectDirectory.file("local.properties")).asText.orNull
        ?.let { load(it.reader()) }
}
fun localProp(key: String, default: String) = (localProps.getProperty(key) ?: providers.gradleProperty(key).orNull ?: default).trim()

android {
    namespace = "com.example.lumeocrtest"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.lumeocrtest"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "OLLAMA_URL", "\"${localProp("lume.ollama.url", "http://127.0.0.1:11434")}\"")
        buildConfigField("String", "OLLAMA_EMBED_MODEL", "\"${localProp("lume.ollama.embedModel", "paraphrase-multilingual")}\"")
        buildConfigField("String", "ROLE_CLASSIFIER_URL", "\"${localProp("lume.roleclassifier.url", "")}\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.18.1")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
