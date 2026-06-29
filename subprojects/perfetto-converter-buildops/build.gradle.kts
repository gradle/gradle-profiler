plugins {
    id("profiler.kotlin-library")
    id("groovy")
    id("profiler.publication")
}

description = "Library for converting Gradle build operation traces into Perfetto traces"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":perfetto-trace-proto"))
    implementation(libs.gson)

    testImplementation(libs.bundles.testDependencies)
}
