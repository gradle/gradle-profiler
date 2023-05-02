plugins {
    id("profiler.java-library")
    id("groovy")
}

description = "Implementation of JProfile profiler integration"

dependencies {
    api(project(":profiler-api"))
}
