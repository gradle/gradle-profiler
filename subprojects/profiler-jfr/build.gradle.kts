plugins {
    id("profiler.java-library")
    id("groovy")
}

description = "Implementation of Java Flight Recorder profiler integration"

dependencies {
    api(project(":profiler-api"))
}
