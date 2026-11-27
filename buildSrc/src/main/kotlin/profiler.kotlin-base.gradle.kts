plugins {
    id("profiler.java-base")
    kotlin("jvm")
}

kotlin {
    jvmToolchain(17)
}

// The Kotlin Gradle Plugin resolves Bouncy Castle into this configuration to sign publications.
// KGP 2.4.x pins 1.84, which is affected by GHSA-9pwp-9qqc-pr26 and GHSA-qp49-qgx5-5m26.
// Remove this once KGP ships with a fixed version.
configurations.matching { it.name == "kotlinBouncyCastleConfiguration" }.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.bouncycastle") {
            useVersion("1.87")
            because("Bouncy Castle 1.84 is affected by GHSA-9pwp-9qqc-pr26 and GHSA-qp49-qgx5-5m26")
        }
    }
}
