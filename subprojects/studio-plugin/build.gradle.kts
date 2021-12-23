import org.jetbrains.intellij.IntelliJPluginConstants.IDEA_CONFIGURATION_NAME
import org.jetbrains.intellij.IntelliJPluginConstants.IDEA_PLUGINS_CONFIGURATION_NAME
import org.jetbrains.intellij.IntelliJPluginConstants.INTELLIJ_DEFAULT_DEPENDENCIES_CONFIGURATION_NAME

plugins {
    groovy
    `java-test-fixtures`
    id("profiler.allprojects")
    id("org.jetbrains.intellij") version "1.2.1"
}

description = "Contains logic for Android Studio plugin that communicates with profiler"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":client-protocol"))
    testFixturesImplementation(project(":client-protocol"))
}

// Applied configurations by gradle-intellij-plugin can be found here:
// https://github.com/JetBrains/gradle-intellij-plugin/blob/master/src/main/kotlin/org/jetbrains/intellij/IntelliJPlugin.kt
val ideaConfiguration = project.configurations.getByName(IDEA_CONFIGURATION_NAME)
val ideaPluginsConfiguration = project.configurations.getByName(IDEA_PLUGINS_CONFIGURATION_NAME)
val intelliJDefaultDependenciesConfiguration = project.configurations.getByName(INTELLIJ_DEFAULT_DEPENDENCIES_CONFIGURATION_NAME)

project.configurations
    .getByName("testFixturesCompileOnly")
    .extendsFrom(ideaConfiguration, ideaPluginsConfiguration, intelliJDefaultDependenciesConfiguration)
project.configurations
    .getByName("testFixturesImplementation")
    .extendsFrom(ideaConfiguration, ideaPluginsConfiguration, intelliJDefaultDependenciesConfiguration)

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

intellij {
    pluginName.set("gradle-profiler-studio-plugin")
    version.set("2021.1.1")
    // Don't override "since-build" and "until-build" properties in plugin.xml,
    // so we don't need to update plugin to use it also on future IntelliJ versions.
    updateSinceUntilBuild.set(false)
    // Any plugin here must be also added to resources/META-INF/plugin.xml
    plugins.set(listOf("java", "gradle", "android"))
}
