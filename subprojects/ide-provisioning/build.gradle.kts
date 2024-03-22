plugins {
    id("profiler.embedded-library")
    id("profiler.publication")
    kotlin("jvm") version "1.9.22"
}

description = "IDE provisioning capabilities for Gradle profiler"

repositories {
    maven { url = uri("https://www.jetbrains.com/intellij-repository/releases") }
    maven { url = uri("https://cache-redirector.jetbrains.com/intellij-dependencies") }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation(libs.ideStarter) {
        exclude(group = "io.ktor")
    }
    testImplementation(libs.bundles.testDependencies)
    testImplementation(libs.commonIo)
}
