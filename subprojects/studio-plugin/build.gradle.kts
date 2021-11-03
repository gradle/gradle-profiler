
plugins {
    groovy
    id("org.jetbrains.intellij") version "1.2.1"
}

description = "Contains logic for Android Studio plugin that communicates with profiler"

dependencies {
    implementation(project(":client-protocol"))
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

intellij {
    pluginName.set("gradle-profiler-studio-plugin")
    version.set("2021.1.1")
    // Any plugin here must be also added to resources/META-INF/plugin.xml
    plugins.set(listOf("java", "gradle", "android"))
}

repositories {
    mavenCentral()
}

