plugins {
    id("profiler.embedded-library")
}

description = "Plugin to collect build operation measurements."

dependencies {
    api(gradleApi())
    implementation(libs.guava)
    implementation(project(":build-operations-measuring"))

    testImplementation(libs.bundles.testDependencies)
}
