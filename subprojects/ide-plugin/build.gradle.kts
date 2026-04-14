import org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask

plugins {
    id("profiler.kotlin-library")
    alias(libs.plugins.intellij.platform)
}

description = "Contains logic for IDE plugin that communicates with profiler"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation(project(":client-protocol"))

    intellijPlatform {
        intellijIdea(libs.versions.intellijPlatformVersion)
        bundledPlugin("com.intellij.java")
        bundledPlugin("com.intellij.gradle")
        bundledPlugin("org.jetbrains.plugins.gradle")
        // JB Android plugin version may need to be bumped in case of IntelliJ platform version bump
        plugin("org.jetbrains.android", libs.versions.jetbrainsAndroidPluginVersion.get())
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
            sinceBuild = libs.versions.minimalSupportedPlatformCode
            untilBuild = provider { null }
        }
    }
    // Disable searchable options indexing since this plugin is not published to the marketplace
    buildSearchableOptions = false
}
