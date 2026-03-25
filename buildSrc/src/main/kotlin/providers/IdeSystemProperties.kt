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

class IdeSystemProperties(
    @get:Internal
    val ideInstallation: IdeInstallation,
    @get:Internal
    val autoDownload: Provider<Boolean>,
    @get:Input
    val runInHeadlessMode: Provider<Boolean>,
    @get:Input
    val homePropertyName: String,
    providers: ProviderFactory
) : CommandLineArgumentProvider {

    @get:Optional
    @get:Nested
    val ideInstallationProvider = providers.provider {
        if (autoDownload.get()) {
            ideInstallation
        } else {
            null
        }
    }

    override fun asArguments(): Iterable<String> {
        val systemProperties = mutableListOf<String>()
        if (autoDownload.get()) {
            val idePath = ideInstallation.installLocation.get().asFile.absolutePath
            systemProperties.add("-D$homePropertyName=$idePath")
        }
        if (runInHeadlessMode.get()) {
            systemProperties.add("-Dstudio.tests.headless=true")
        }
        return systemProperties
    }
}

abstract class IdeInstallation {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val installLocation: DirectoryProperty
}
