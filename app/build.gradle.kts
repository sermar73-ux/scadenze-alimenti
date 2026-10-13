plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "it.sergio.scadenze"
    compileSdk = 34

    defaultConfig {
        applicationId = "it.sergio.scadenze"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // Chiave di prova fissa: così ogni APK nuovo si installa sopra il precedente
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core:1.13.1")
    // Pagina servita da un indirizzo https interno: fotocamera e salvataggi funzionano come sul sito
    implementation("androidx.webkit:webkit:1.11.0")
    // Codici a barre e date lette sul telefono, modelli inclusi nell'app: funzionano offline
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
}
