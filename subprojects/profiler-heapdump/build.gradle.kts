plugins {
    id("profiler.java-library")
    id("groovy")
}

description = "Implementation of heap dump profiler integration"

dependencies {
    api(project(":profiler-api"))
    api(project(":profiler-gradle-only-api"))
}
