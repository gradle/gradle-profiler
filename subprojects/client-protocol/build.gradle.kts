plugins {
    id("groovy")
    id("profiler.embedded-library")
    id("profiler.publication")
}

dependencies {
    testImplementation(versions.groovy)
    testImplementation(versions.spock)
}
