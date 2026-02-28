import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    `java-test-fixtures`
    id("profiler.kotlin-library")
    id("org.jetbrains.intellij.platform") version "2.11.0"
}

description = "IntelliJ platform plugin that communicates with the profiler process for IDE-based profiling"

// IntelliJ Platform 2025.1+ requires Java 21; override the project-wide Java 17 toolchain.
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
kotlin {
    jvmToolchain(21)
}

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
        intellijIdeaCommunity("2025.1.1")
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.plugins.gradle")
        testFramework(TestFrameworkType.Platform)
    }
}

// Add IntelliJ Platform JARs to the testFixtures compile classpath.
// IntelliJ Platform Gradle Plugin 2.x wires platform JARs to the main and test
// compile classpaths automatically, but not to testFixtures.  Since testFixtures
// are now pure Kotlin (Groovy files were removed to avoid Groovy 3 / Java 21 ASM
// incompatibility), the Kotlin compiler can handle Java 21 class files directly.
afterEvaluate {
    configurations.findByName("intellijPlatform")?.let { platformConfig ->
        configurations.getByName("testFixturesCompileOnly").extendsFrom(platformConfig)
        // The test source set also needs to see the platform JARs; the plugin may
        // not wire them to testCompileOnly/testImplementation in all versions.
        configurations.getByName("testCompileOnly").extendsFrom(platformConfig)
        configurations.getByName("testImplementation").extendsFrom(platformConfig)
    }
}

tasks.test {
    // IntelliJ's HeavyPlatformTestCase is based on JUnit 4.
    useJUnit()
    // Disable IntelliJ file system access check for tests: having this check enabled can
    // fail CI builds when Gradle user home is mounted, e.g. /mnt/tcagent1/.gradle.
    systemProperty("NO_FS_ROOTS_ACCESS_CHECK", "true")
}

intellijPlatform {
    pluginConfiguration {
        // Display name shown in the IDE plugin manager
        name = "Gradle Profiler IntelliJ Plugin"
        // Don't override since-build/until-build; they are set in plugin.xml
        ideaVersion {
            sinceBuild = provider { null }
            untilBuild = provider { null }
        }
    }
    // Disable searchable options indexing since this plugin is not published to the marketplace
    buildSearchableOptions = false
}
