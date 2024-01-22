plugins {
    id("profiler.embedded-library")
}

dependencies {
    java17Implementation(libs.ideStarter) {
        exclude(group = "io.ktor")
        exclude(group = "com.jetbrains.infra")
        exclude(group = "com.jetbrains.intellij.remoteDev")
    }
    testImplementation(libs.bundles.testDependencies)
    testImplementation(libs.commonIo)
}
