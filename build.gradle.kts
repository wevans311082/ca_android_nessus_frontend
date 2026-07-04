// Legacy buildscript kept for compatibility. Prefer pluginManagement + version catalog in settings.gradle.kts.
// Keep versions in sync with gradle/libs.versions.toml
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.7.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.25")
    }
}
