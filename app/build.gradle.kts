plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.ryccoatika.contactmanager"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.ryccoatika.contactmanager"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Shared debug keystore so every machine/CI produces the same debug
        // signature (stable SHA-1 for API-console registrations). These are the
        // well-known public Android debug credentials — not secrets.
        getByName("debug") {
            storeFile = rootProject.file("release/app-debug.jks")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // Real release key. Guarded by existence so the project still builds on
        // machines/CI without the keystore. Passwords come from Gradle properties
        // (~/.gradle/gradle.properties or -P/env) — never hardcoded here.
        if (rootProject.file("release/app-release.jks").exists()) {
            create("release") {
                storeFile = rootProject.file("release/app-release.jks")
                storePassword = properties["CONTACTMANAGER_RELEASE_KEYSTORE_PWD"]?.toString().orEmpty()
                keyAlias = "contactmanager"
                keyPassword = properties["CONTACTMANAGER_RELEASE_KEY_PWD"]?.toString().orEmpty()
            }
        }
    }

    buildTypes {
        debug {
            // Picks up the reconfigured debug signingConfig above.
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            // Signed only when release/app-release.jks is present; null → unsigned.
            signingConfig = signingConfigs.findByName("release")
            // R8 in full mode (default since AGP 8.0; pinned in gradle.properties).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                // Optimizing defaults — never proguard-android.txt (forces -dontoptimize).
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.coil.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.datastore.preferences)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}