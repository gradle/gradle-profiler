package tasks

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.net.URI
import java.security.MessageDigest

/**
 * Fetches the pinned Perfetto trace proto from its upstream release and verifies it by checksum,
 * instead of vendoring ~16k lines of generated proto into the repository.
 *
 * To update Perfetto, bump [version] and [sha256] together (sha256 of the raw upstream file).
 */
@DisableCachingByDefault(because = "Downloads a single pinned file; not worth caching")
abstract class FetchPerfettoProtoTask : DefaultTask() {

    init {
        description = "Downloads and checksum-verifies the pinned Perfetto trace proto."
    }

    @get:Input
    abstract val version: Property<String>

    @get:Input
    abstract val sha256: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun fetch() {
        val version = version.get()
        val expectedSha = sha256.get()
        val url =
            "https://raw.githubusercontent.com/google/perfetto/$version/protos/perfetto/trace/perfetto_trace.proto"

        val bytes = URI.create(url).toURL().openStream().use { it.readBytes() }
        val actualSha = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        if (actualSha != expectedSha) {
            throw GradleException(
                "Checksum mismatch for $url\n  expected: $expectedSha\n  actual:   $actualSha"
            )
        }

        // Upstream does not set java_multiple_files; the converter relies on the resulting top-level
        // generated classes (e.g. perfetto.protos.TracePacket), so we inject it after verification.
        val patched = String(bytes, Charsets.UTF_8)
            .replaceFirst("package perfetto.protos;", "package perfetto.protos;\noption java_multiple_files = true;")

        val target = outputDir.get().file("perfetto/trace/perfetto_trace.proto").asFile
        target.parentFile.mkdirs()
        target.writeText(patched)
    }
}
