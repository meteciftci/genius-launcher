import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.evim.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.evim.tv"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        // Tuya QR girişinin istemci kimliği koda değil git'e girmeyen local.properties'e yazılır
        val local = Properties().apply {
            rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
        }
        buildConfigField("String", "TUYA_CLIENT_ID", "\"${local.getProperty("tuya.clientId", "")}\"")
        buildConfigField("String", "TUYA_SCHEMA", "\"${local.getProperty("tuya.schema", "")}\"")
    }

    // Yayın anahtarı repo dışında durur, parolası macOS Anahtar Zinciri'nden okunur. Anahtarı olmayanlar
    // (başka geliştiriciler) release derlemesini debug anahtarıyla imzalar.
    val releaseKey = File(System.getProperty("user.home"), ".android/genius-release.jks")
    if (releaseKey.exists()) {
        signingConfigs.create("release") {
            val password = providers.exec {
                commandLine("security", "find-generic-password", "-s", "genius-launcher-release", "-w")
            }.standardOutput.asText.get().trim()
            storeFile = releaseKey
            storePassword = password
            keyAlias = "genius"
            keyPassword = password
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.palette)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.dadb)
    implementation(libs.zxing.core)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.rtsp)
    implementation(libs.media3.ui)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
