plugins {
    java
    id("org.jetbrains.intellij") version "1.2.1"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":client-protocol"))
}

intellij {
    pluginName.set("gradle-profiler-studio-plugin")
    version.set("2021.1.1")
    // Any plugin here must be also added to resources/META-INF/plugin.xml
    plugins.set(listOf("java", "gradle", "android"))

    // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file.
//    plugins.set(properties("platformPlugins").split(',').map(String::trim).filter(String::isNotEmpty))
}
