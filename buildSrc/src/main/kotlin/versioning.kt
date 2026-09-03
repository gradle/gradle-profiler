import org.gradle.api.Project
import java.nio.file.Files
import java.util.Properties

/**
 * Resolves the profiler version without observing the state of another project.
 *
 * Each project computes the version from the same two immutable inputs: the build
 * receipt that CI drops into the root directory for a promotion build, and the
 * `profiler.version` Gradle property. Sharing the value through an extension on
 * the root project instead would break Isolated Projects.
 */
fun Project.resolveProfilerVersion(): String {
    val rootDir = isolated.rootProject.projectDirectory.asFile
    val incomingBuildReceipt = rootDir.resolve("incoming-distributions/$buildReceiptName")
    if (incomingBuildReceipt.isFile) {
        val properties = Properties()
        Files.newInputStream(incomingBuildReceipt.toPath()).use { properties.load(it) }
        return properties.getProperty("version")
    }
    return providers.gradleProperty("profiler.version").get()
}
