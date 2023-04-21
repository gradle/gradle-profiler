package tasks

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.tooling.GradleConnector
import java.io.File

/**
 * Installs Android SDK. This task requires the ANDROID_HOME or ANDROID_SDK_ROOT env variable to be set.
 */
@UntrackedTask(because = "Output directory can change when running other builds")
abstract class InstallAndroidSdkTask : DefaultTask() {

    @get:Input
    abstract val androidSdkVersion: Property<String>

    @get:OutputDirectory
    abstract val androidProjectDir: DirectoryProperty

    @TaskAction
    fun install() {
        if (System.getenv("ANDROID_HOME") == null && System.getenv("ANDROID_SDK_ROOT") == null) {
            throw GradleException(
                "None of ANDROID_HOME or ANDROID_SDK_ROOT env variable is set, but one should be. " +
                    "The installation directory should also contain the Android sdk license key. See https://developer.android.com/studio/intro/update.html#download-with-gradle."
            )
        }

        val androidSdkVersion = androidSdkVersion.get()
        val projectDir = androidProjectDir.get().asFile

        createAndroidProject(projectDir, androidSdkVersion)
        buildAndroidProject(projectDir)
    }

    private fun buildAndroidProject(projectDir: File) {
        val connector = GradleConnector.newConnector()
            .forProjectDirectory(projectDir)
        connector.connect().use {
            it.newBuild().forTasks("build")
                .addArguments("--stacktrace")
                .setStandardError(System.err)
                .setStandardOutput(System.out)
                .run()
        }
    }

    private fun createAndroidProject(projectDir: File, androidSdkVersion: String) {
        File(projectDir, "settings.gradle").writeText(
            """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    google()
                    mavenCentral()
                }
            }
            rootProject.name = "android-sdk-project"
            """.trimIndent()
        )

        File(projectDir, "build.gradle").writeText(
            """
            plugins {
                id 'com.android.application' version "$androidSdkVersion"
            }
            repositories {
                google()
                mavenCentral()
            }
            android {
                compileSdk 31
            }
            """.trimIndent()
        )

        File(projectDir, "src/main").mkdirs()
        File(projectDir, "src/main/AndroidManifest.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="com.example.myapplication">
            </manifest>
            """.trimIndent()
        )
    }
}
