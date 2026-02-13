plugins {
    id("profiler.kotlin-library")
    id("groovy")
    id("profiler.publication")
    alias(libs.plugins.protobuf)
}

description = "Library for converting Gradle build operation traces into Perfetto traces"

repositories {
    mavenCentral()
}

protobuf {
    protoc {
        artifact = libs.protobuf.protoc.get().toString()
    }
}

dependencies {
    api(libs.protobuf.java)
    implementation(libs.gson)

    testImplementation(libs.bundles.testDependencies)
}
