plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Numéro de version = numéro du run GitHub Actions : il augmente à chaque compilation,
// ce qui permet à l'application de savoir si une version plus récente est publiée.
val numeroVersion = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "fr.adftar.principale"
    compileSdk = 34

    defaultConfig {
        applicationId = "fr.adftar.principale"
        minSdk = 26
        targetSdk = 34
        versionCode = numeroVersion
        versionName = "1.$numeroVersion"
    }

    signingConfigs {
        // Clé fixe (app/debug.keystore) : chaque nouvel APK s'installe par-dessus le
        // précédent sans désinstaller. Sans elle, la clé debug par défaut change à
        // chaque compilation GitHub et la mise à jour par le logo échoue.
        getByName("debug") {
            val cle = file("debug.keystore")
            if (cle.exists()) {
                storeFile = cle
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        buildConfig = true
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
    // FileProvider, pour transmettre l'APK de mise à jour à l'installeur Android.
    implementation("androidx.core:core:1.13.1")
}
