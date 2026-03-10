import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask
import org.jetbrains.kotlin.gradle.utils.extendsFrom

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

// Add IntelliJ Platform JARs to the testFixtures compile classpath.
// In 2.x the plugin wires platform JARs to main/test automatically, but not testFixtures.
// Only compileOnly is needed: at runtime the plugin's test sandbox provides platform classes.
// Avoid using implementation here: consumers of testFixtures resolve its runtime classpath,
// which would leak platform deps and require them to configure IntelliJ Platform repositories.
configurations {
    testFixturesCompileOnly.extendsFrom(intellijPlatformClasspath)
}

dependencies {
    implementation(project(":client-protocol"))
    testImplementation(libs.bundles.testDependencies)
    // IntelliJ's JUnit5TestSessionListener (from TestFrameworkType.Platform) references junit-jupiter-api,
    // but Spock only brings junit-platform-engine.
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testFixturesImplementation(project(":client-protocol"))
    testFixturesImplementation(libs.bundles.testDependencies)

    intellijPlatform {
        androidStudio("2024.1.1.11")
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.plugins.gradle")
        bundledPlugin("org.jetbrains.android")
        testFramework(TestFrameworkType.Platform)
    }
}

// Exclude Spock from test sandbox to avoid duplicate on classpath (Gradle provides it at runtime)
tasks.withType<PrepareSandboxTask> {
    exclude("**/spock-*.jar")
}

tasks.test {
    useJUnitPlatform()
    // Disable IntelliJ file system access check for tests: having this check enabled can fail
    // CI builds since Gradle user home can be mounted, e.g. it can be located in the /mnt/tcagent1/.gradle
    systemProperty("NO_FS_ROOTS_ACCESS_CHECK", "true")
    // Set idea.home.path - required by IntelliJ test framework but not set by 2.x plugin
    systemProperty("idea.home.path", intellijPlatform.platformPath.toString())
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
