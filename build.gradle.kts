// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.spotless)
}

// ktlint via Spotless: `spotlessApply` formats, `spotlessCheck` gates (CI + /lint).
// Rule tuning lives in .editorconfig, not here.
spotless {
    kotlin {
        target("app/src/**/*.kt")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        target("*.gradle.kts", "app/*.gradle.kts")
        ktlint(libs.versions.ktlint.get())
    }
}

// Convenience alias so `./gradlew -q changelogs` prints the current version's
// notes (delegates to the gradle-changelog-plugin task registered on :app).
tasks.register("changelogs") {
    dependsOn(":app:getChangelog")
}
