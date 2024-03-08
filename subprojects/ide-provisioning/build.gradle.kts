plugins {
    id("profiler.embedded-library")
    id("profiler.publication")
    kotlin("jvm") version "1.9.22"
}

description = "IDE provisioning capabilities for Gradle profiler"

repositories {  }

dependencies {
    implementation(libs.ideStarter) {
        exclude(group = "io.ktor")
    }
    testImplementation(libs.bundles.testDependencies)
    testImplementation(libs.commonIo)
}
