plugins {
    id("profiler.java-library")
    id("groovy")
    id("profiler.publication")
}

description = "Library for converting JFR recordings into Perfetto traces"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":perfetto-trace-proto"))
    compileOnly("org.jspecify:jspecify:1.0.0")

    testImplementation(libs.bundles.testDependencies)
}
