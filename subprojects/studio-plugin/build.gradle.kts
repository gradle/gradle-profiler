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
    pluginName.set("test-plugin")
    version.set("2021.1.1")
    plugins.set(listOf("java", "gradle"))

    // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file.
//    plugins.set(properties("platformPlugins").split(',').map(String::trim).filter(String::isNotEmpty))
}
