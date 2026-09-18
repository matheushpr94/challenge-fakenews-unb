plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "app.lume.mvp"
    compileSdk = 35
    defaultConfig {
        applicationId = "app.lume.mvp"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
    }
    flavorDimensions += "connection"
    productFlavors {
        create("demo") {
            dimension = "connection"
            versionNameSuffix = "-demo"
            buildConfigField("boolean", "DEMO_ONLY", "true")
        }
        create("connected") {
            dimension = "connection"
            applicationIdSuffix = ".connected"
            buildConfigField("boolean", "DEMO_ONLY", "false")
        }
    }
    buildTypes { getByName("release") { isMinifyEnabled = false } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = true }
}
dependencies { testImplementation("junit:junit:4.13.2") }
