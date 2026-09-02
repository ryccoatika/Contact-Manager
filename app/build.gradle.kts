plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.changelog)
}

// Firebase is optional: wire google-services only when the json (kept in the
// gitignored release/ folder, next to the keystores) is present. The plugin
// only scans app/, so bridge the file into place first. Absent → no FirebaseApp
// → NoOpAnalytics, and the build/tests still pass.
//
// The Crashlytics plugin is mandatory whenever the crashlytics SDK ships: it
// generates the com.google.firebase.crashlytics.build_id resource that
// CrashlyticsCore.onPreExecute() requires, and its absence is a hard crash at
// FirebaseApp init — not a degraded no-op.
val googleServicesJson = rootProject.file("release/google-services.json")
if (googleServicesJson.exists()) {
    googleServicesJson.copyTo(file("google-services.json"), overwrite = true)
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

// versionCode is CI-driven so Play testing tracks always get a monotonically
// increasing code. CI passes -PappVersionCode=<git commit count>; default 1 locally.
val appVersionCode = (project.findProperty("appVersionCode") as String?)?.toIntOrNull() ?: 1
// Version name lives in gradle.properties (single source shared with CI) — used
// by the manifest and the gradle-changelog-plugin (so `getChangelog` returns
// this version's section).
val appVersionName = (project.findProperty("appVersionName") as String?) ?: "1.0"

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
        versionCode = appVersionCode
        versionName = appVersionName

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
            // Bundle native debug symbols into the AAB so Play can symbolicate
            // native crash stack traces — silences the "App Bundle contains native
            // code without debug symbols" upload warning. Play reads them straight
            // from the bundle, so no extra upload step is needed.
            ndk {
                debugSymbolLevel = "FULL"
            }
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

changelog {
    version.set(appVersionName)
    // CHANGELOG.md lives at the repo root, not inside the module.
    path.set(rootProject.file("CHANGELOG.md").canonicalPath)
    // Our versionName is "1.0", not strict SemVer — accept 2+ number segments.
    headerParserRegex.set("""\d+(\.\d+)+""".toRegex())
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
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
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.play.review)
    implementation(libs.play.app.update)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}