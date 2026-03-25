package extensions

import org.gradle.api.provider.Property

interface IntellijTestExtension {
    val autoDownloadIntellij: Property<Boolean>
    val testIntellijVersion: Property<String>
    val runIntellijInHeadlessMode: Property<Boolean>
}
