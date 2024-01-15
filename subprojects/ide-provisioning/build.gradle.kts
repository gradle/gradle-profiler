plugins {
    id("profiler.embedded-library")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    maven { url = uri("https://www.jetbrains.com/intellij-repository/releases") }
    maven { url = uri("https://cache-redirector.jetbrains.com/intellij-dependencies") }

    maven { url = uri("https://cache-redirector.jetbrains.com/maven-central") }
    maven { url = uri("https://www.jetbrains.com/intellij-repository/snapshots") }
    maven {
        url = uri("https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/grazi/grazie-platform-public")
    }
}

dependencies {
    implementation("com.jetbrains.intellij.tools:ide-starter-squashed:233.13135.103") {
        exclude(group = "io.ktor")
        exclude(group = "com.jetbrains.infra")
        exclude(group = "com.jetbrains.intellij.remoteDev")
    }
    testImplementation(libs.bundles.testDependencies)
}
