package extensions

import org.gradle.api.provider.Property

interface AndroidStudioTestExtension {
    val autoDownload: Property<Boolean>
    val version: Property<String>
    val codename: Property<String>
    val headlessMode: Property<Boolean>
    val autoDownloadAndroidSdk: Property<Boolean>
    val sdkVersion: Property<String>
}
