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
        intellijIdea("2025.3")
        bundledPlugin("com.intellij.java")
        bundledPlugin("com.intellij.gradle")
        bundledPlugin("org.jetbrains.plugins.gradle")
        plugin("org.jetbrains.android", "253.28294.334")
    }
}

// IntelliJ 2025.3 is compiled for Java 21
kotlin {
    jvmToolchain(21)
}

// Exclude Spock from test sandbox to avoid duplicate on classpath (Gradle provides it at runtime)
tasks.withType<PrepareSandboxTask> {
    exclude("**/spock-*.jar")
}

intellijPlatform {
    pluginConfiguration {
        name = "gradle-profiler-studio-plugin"
        ideaVersion {
            sinceBuild = provider { "253" }
            untilBuild = provider { null }
        }
    }
    // Disable searchable options indexing since this plugin is not published to the marketplace
    buildSearchableOptions = false
}
