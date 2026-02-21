import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    groovy
    `java-test-fixtures`
    id("profiler.kotlin-library")
    id("org.jetbrains.intellij.platform") version "2.11.0"
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
    testImplementation(libs.bundles.testDependencies)
    testFixturesImplementation(project(":client-protocol"))
    testFixturesImplementation(libs.bundles.testDependencies)

    intellijPlatform {
        // Target Android Studio Hedgehog, equivalent to the previous IntelliJ 2023.1.1 + Android plugin config.
        androidStudio("2023.1.1.28")
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.plugins.gradle")
        bundledPlugin("org.jetbrains.android")
        testFramework(TestFrameworkType.Platform)
    }
}

// Add IntelliJ Platform JARs to the testFixtures compile classpath.
// In 2.x the plugin wires platform JARs to main/test automatically, but not testFixtures.
afterEvaluate {
    configurations.findByName("intellijPlatform")?.let { platformConfig ->
        configurations.getByName("testFixturesCompileOnly").extendsFrom(platformConfig)
        configurations.getByName("testFixturesImplementation").extendsFrom(platformConfig)
    }
}

tasks.test {
    useJUnitPlatform()
    // Disable IntelliJ file system access check for tests: having this check enabled can fail
    // CI builds since Gradle user home can be mounted, e.g. it can be located in the /mnt/tcagent1/.gradle
    systemProperty("NO_FS_ROOTS_ACCESS_CHECK", "true")
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
