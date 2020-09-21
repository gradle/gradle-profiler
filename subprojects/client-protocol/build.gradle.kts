plugins {
    id("profiler.embedded-library")
    id("groovy")
}

dependencies {
    testImplementation(versions.groovy)
    testImplementation(versions.spock)
}
