import org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask

plugins {
    id("profiler.kotlin-library")
    alias(libs.plugins.intellij.platform)
}

description = "Contains logic for Android Studio plugin that communicates with profiler"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation(project(":client-protocol"))

    intellijPlatform {
        androidStudio("2024.1.1.11")
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.plugins.gradle")
        bundledPlugin("org.jetbrains.android")
    }
}

// Exclude Spock from test sandbox to avoid duplicate on classpath (Gradle provides it at runtime)
tasks.withType<PrepareSandboxTask> {
    exclude("**/spock-*.jar")
}

intellijPlatform {
    pluginConfiguration {
        name = "gradle-profiler-studio-plugin"
        ideaVersion {
            sinceBuild = provider { "231" }
            untilBuild = provider { null }
        }
    }
    // Disable searchable options indexing since this plugin is not published to the marketplace
    buildSearchableOptions = false
}
