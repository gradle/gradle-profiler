plugins {
    id("profiler.java-library")
    id("groovy")
}

description = "Implementation of chrome trace profiler integration"

dependencies {
    api(project(":profiler-api"))
    api(project(":profiler-gradle-only-api"))
}
