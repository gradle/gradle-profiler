plugins {
    id("profiler.java-library")
    id("profiler.publication")
}

description = "Facilities for declaring and parsing data coming out of scenario file"

dependencies {
    api(libs.typesafe.config)
}
