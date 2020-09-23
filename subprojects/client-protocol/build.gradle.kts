plugins {
    id("profiler.embedded-library")
    id("groovy")
    id("profiler.publication")
}

dependencies {
    testImplementation(versions.groovy)
    testImplementation(versions.spock)
}
