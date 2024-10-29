import org.jetbrains.intellij.IntelliJPluginConstants.IDEA_CONFIGURATION_NAME
import org.jetbrains.intellij.IntelliJPluginConstants.IDEA_PLUGINS_CONFIGURATION_NAME
import org.jetbrains.intellij.IntelliJPluginConstants.INTELLIJ_DEFAULT_DEPENDENCIES_CONFIGURATION_NAME

plugins {
    groovy
    `java-test-fixtures`
    id("profiler.allprojects")
    id("org.jetbrains.intellij") version "1.17.4"
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
}

description = "Contains logic for Android Studio plugin that communicates with profiler"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":client-protocol"))
    testImplementation(libs.bundles.testDependencies)
    testFixturesImplementation(project(":client-protocol"))
    testFixturesImplementation(libs.bundles.testDependencies)
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
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.test {
    useJUnitPlatform()
    // Disable IntelliJ file system access check for tests: having this check enabled can fail
    // CI builds since Gradle user home can be mounted, e.g. it can be located in the /mnt/tcagent1/.gradle
    systemProperty("NO_FS_ROOTS_ACCESS_CHECK", "true")
}

intellij {
    pluginName.set("gradle-profiler-studio-plugin")
    version.set("2023.1.1")
    // Don't override "since-build" and "until-build" properties in plugin.xml,
    // so we don't need to update plugin to use it also on future IntelliJ versions.
    updateSinceUntilBuild.set(false)
    // Any plugin here must be also added to resources/META-INF/plugin.xml
    plugins.set(listOf("java", "gradle", "org.jetbrains.android"))
}
