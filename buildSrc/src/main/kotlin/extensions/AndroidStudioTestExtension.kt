package extensions

import org.gradle.api.provider.Property

interface AndroidStudioTestExtension {

    val autoDownloadAndroidStudio: Property<Boolean>
    val testAndroidStudioVersion: Property<String>
    val runAndroidStudioInHeadlessMode: Property<Boolean>
    val autoDownloadAndroidSdk: Property<Boolean>
    val testAndroidSdkVersion: Property<String>

}
