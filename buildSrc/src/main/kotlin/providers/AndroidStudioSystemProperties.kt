package providers

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.CommandLineArgumentProvider
import java.io.File

class AndroidStudioSystemProperties(
    @get:Internal
    val studioInstallation: AndroidStudioInstallation,
    @get:Internal
    val autoDownloadAndroidStudio: Provider<Boolean>,
    @get:Input
    val runInHeadlessMode: Provider<Boolean>,
    providers: ProviderFactory
) : CommandLineArgumentProvider {

    @get:Optional
    @get:Nested
    val studioInstallationProvider = providers.provider {
        if (autoDownloadAndroidStudio.get()) {
            studioInstallation
        } else {
            null
        }
    }

    override fun asArguments(): Iterable<String> {
        val systemProperties = mutableListOf<String>()
        if (autoDownloadAndroidStudio.get()) {
            val androidStudioPath = studioInstallation.studioInstallLocation.get().asFile.absolutePath
            val isMacOS = System.getProperty("os.name").toLowerCase().startsWith("mac")
            val macOsAndroidStudioPath = "$androidStudioPath/Android Studio.app"
            val macOsAndroidStudioPathPreview = "$androidStudioPath/Android Studio Preview.app"
            val windowsAndLinuxPath = "$androidStudioPath/android-studio"
            val studioHome = when {
                isMacOS && File(macOsAndroidStudioPath).exists() -> macOsAndroidStudioPath
                isMacOS -> macOsAndroidStudioPathPreview
                else -> windowsAndLinuxPath
            }
            systemProperties.add("-Dstudio.home=$studioHome")
        }
        if (runInHeadlessMode.get()) {
            systemProperties.add("-Dstudio.tests.headless=true")
        }
        return systemProperties
    }
}

abstract class AndroidStudioInstallation {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val studioInstallLocation: DirectoryProperty
}
