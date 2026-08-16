import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.langualens.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.langualens.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "2.0"
        vectorDrawables { useSupportLibrary = true }
    }

    // The keystore is never committed. Locally it sits at app/langualens-release.jks
    // with its passwords in a gitignored keystore.properties; in CI both come from
    // GitHub Actions secrets. If neither is present the release build falls back to
    // the debug signing key so `assembleRelease` still works for a plain checkout.
    val keystoreFile = file(System.getenv("KEYSTORE_FILE") ?: "langualens-release.jks")
    val keystoreProps = rootProject.file("keystore.properties").let { f ->
        Properties().apply { if (f.exists()) f.inputStream().use { load(it) } }
    }
    fun secret(env: String, prop: String): String? =
        System.getenv(env) ?: keystoreProps.getProperty(prop)

    val hasReleaseKey = keystoreFile.exists() && secret("KEYSTORE_PASSWORD", "storePassword") != null

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = keystoreFile
                storePassword = secret("KEYSTORE_PASSWORD", "storePassword")
                keyAlias = secret("KEY_ALIAS", "keyAlias") ?: "tolk"
                keyPassword = secret("KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = if (hasReleaseKey) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.activity:activity-compose:1.9.0")

    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("com.google.mlkit:translate:17.0.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    debugImplementation(composeBom)
    debugImplementation("androidx.compose.ui:ui-tooling")
}
