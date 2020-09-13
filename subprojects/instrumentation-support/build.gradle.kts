plugins {
    id("profiler.embedded-library")
}

dependencies {
    implementation(versions.toolingApi)
    implementation(project(":client-protocol"))
}
