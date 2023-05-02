plugins {
    id("profiler.java-library")
    id("groovy")
}

description = "APIs required to invoke different build tools"

dependencies {
    api(project(":profiler-api"))
}
