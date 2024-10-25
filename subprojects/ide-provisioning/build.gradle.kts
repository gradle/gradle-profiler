plugins {
    id("profiler.embedded-library")
    kotlin("jvm") version "1.9.22"
}

description = "IDE provisioning capabilities for Gradle profiler"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation(project(":ide-provisioning-api"))
    implementation(libs.ideStarter) {
        exclude(group = "io.ktor")
    }
    implementation("org.kodein.di:kodein-di-jvm:7.20.2")
    testImplementation(libs.bundles.testDependencies)
    testImplementation(libs.commonIo)
}
