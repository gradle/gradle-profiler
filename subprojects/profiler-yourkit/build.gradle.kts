plugins {
    id("profiler.java-library")
    id("groovy")
}

description = "Implementation of YourKit profiler integration"

dependencies {
    api(project(":profiler-api"))
}
