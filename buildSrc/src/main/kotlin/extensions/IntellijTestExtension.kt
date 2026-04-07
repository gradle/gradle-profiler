package extensions

import org.gradle.api.provider.Property

interface IntellijTestExtension {
    val autoDownload: Property<Boolean>
    val version: Property<String>
    val headlessMode: Property<Boolean>
}
