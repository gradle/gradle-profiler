plugins {
    id("profiler.publication")
    id("profiler.kotlin-library")
}

kotlin {
    jvmToolchain(17)
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://www.jetbrains.com/intellij-repository/releases")
    }

    maven {
        url = uri("https://cache-redirector.jetbrains.com/intellij-dependencies")
    }
}

dependencies {
    implementation(project(":build-action"))
    implementation(project(":scenario-definition"))
    implementation(project(":client-protocol"))

    implementation(libs.bundles.intellijIdeStarter)
    implementation(libs.bundles.intellijIdeDriver)
    implementation(libs.bundles.intellijIdeMetrics)

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("net.sf.jopt-simple:jopt-simple:5.0.4")
}
