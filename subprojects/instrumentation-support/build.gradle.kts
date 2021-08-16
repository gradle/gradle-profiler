plugins {
    id("profiler.embedded-library")
}

dependencies {
    implementation(libs.toolingApi)
    implementation(project(":client-protocol"))
}
