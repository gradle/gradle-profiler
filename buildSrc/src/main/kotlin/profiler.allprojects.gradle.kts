plugins {
    id("profiler.versioning")
    base
}

group = "org.gradle.profiler"

tasks.register("sanityCheck")

tasks.named("check").configure { dependsOn("sanityCheck") }
