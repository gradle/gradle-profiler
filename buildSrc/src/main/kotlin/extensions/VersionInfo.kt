package extensions

import org.gradle.api.provider.Property

interface VersionInfo {
    val version: Property<String>
}
