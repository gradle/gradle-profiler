import org.gradle.api.Project
import org.gradle.api.provider.Provider
import java.io.StringReader
import java.util.Properties

/** The version to publish: the promoted version from the incoming build receipt, or the `profiler.version` property. */
fun Project.profilerVersion(): Provider<String> {
    val incomingBuildReceipt = layout.settingsDirectory.file("incoming-distributions/$buildReceiptName")
    val receiptVersion = providers.fileContents(incomingBuildReceipt).asText.map { text ->
        // Falling back to the property would publish a version other than the promoted one.
        parseProperties(text)["version"] ?: error("No 'version' property in $incomingBuildReceipt")
    }
    return receiptVersion.orElse(providers.gradleProperty("profiler.version"))
}

private fun parseProperties(text: String): Map<String, String> {
    val properties = Properties()
    StringReader(text).use { properties.load(it) }
    return properties.entries.associate { (key, value) -> key.toString() to value.toString() }
}
