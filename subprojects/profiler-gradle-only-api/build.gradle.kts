plugins {
    id("profiler.java-library")
    id("groovy")
}

description = "APIs available to profilers that only support Gradle"

dependencies {
    api(project(":profiler-api"))
    api(gradleApi())
}
