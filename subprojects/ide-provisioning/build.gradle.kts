plugins {
    id("profiler.embedded-library")
    id("profiler.publication")
}

description = "IDE provisioning capabilities for Gradle profiler"

dependencies {
    implementation(libs.ideStarter) {
        exclude(group = "io.ktor")
        exclude(group = "com.jetbrains.infra")
        exclude(group = "com.jetbrains.intellij.remoteDev")
    }
    testImplementation(libs.bundles.testDependencies)
    testImplementation(libs.commonIo)
}
