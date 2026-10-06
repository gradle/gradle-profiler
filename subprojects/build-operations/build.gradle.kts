plugins {
    id("profiler.embedded-library")
}

description = "Plugin to collect build operation measurements."

dependencies {
    api(gradleApi())
    implementation(libs.guava)
    implementation(project(":build-operations-measuring"))
    implementation(project(":profilers-support"))

    testImplementation(libs.bundles.testDependencies)
}
