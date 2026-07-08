package tasks

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.net.HttpURLConnection
import java.net.URI

/**
 * Polls [url] with HEAD requests until it resolves (HTTP 2xx) or [timeoutMinutes] elapses.
 *
 * This exists to front the SDKMAN release: SDKMAN's API downloads the release URL itself and rejects
 * the release with an opaque `HTTP 400 Bad Request` if it cannot resolve it (the Gradle plugin then
 * hides the real "URL cannot be resolved" message). Right after a Maven Central publish the artifact
 * often hasn't propagated yet, so we wait here and, if it never becomes available, fail with a clear
 * message that names the actual problem instead of leaving a confusing 400 behind.
 */
@DisableCachingByDefault(because = "Polls a remote URL; nothing to cache")
abstract class AwaitUrlTask : DefaultTask() {

    init {
        description = "Waits until a URL resolves before dependent tasks run."
    }

    @get:Input
    abstract val url: Property<String>

    @get:Input
    abstract val timeoutMinutes: Property<Long>

    @get:Input
    abstract val pollIntervalSeconds: Property<Long>

    @TaskAction
    fun await() {
        val downloadUrl = url.get()
        val target = URI(downloadUrl).toURL()
        val pollIntervalMillis = pollIntervalSeconds.get() * 1_000
        val deadline = System.currentTimeMillis() + timeoutMinutes.get() * 60_000
        var attempt = 0
        var lastProblem: String
        while (true) {
            attempt++
            lastProblem = try {
                val connection = (target.openConnection() as HttpURLConnection).apply {
                    requestMethod = "HEAD"
                    connectTimeout = 15_000
                    readTimeout = 15_000
                    instanceFollowRedirects = true
                }
                val code = try {
                    connection.responseCode
                } finally {
                    connection.disconnect()
                }
                if (code in 200..299) {
                    logger.lifecycle("URL is available (attempt $attempt): $downloadUrl")
                    return
                }
                "HTTP $code"
            } catch (e: Exception) {
                e.message ?: e.toString()
            }
            if (System.currentTimeMillis() + pollIntervalMillis >= deadline) {
                throw GradleException(
                    "URL is not accessible after ${timeoutMinutes.get()} minutes: $downloadUrl\n" +
                        "Last check failed with: $lastProblem.\n" +
                        "This is the real reason a SDKMAN release fails here: its API downloads this URL and " +
                        "rejects the release with 'HTTP 400 Bad Request (URL cannot be resolved)'. The artifact " +
                        "has most likely not propagated to Maven Central yet — re-run once it is available."
                )
            }
            logger.lifecycle(
                "URL not available yet (attempt $attempt, $lastProblem): $downloadUrl — " +
                    "retrying in ${pollIntervalSeconds.get()}s"
            )
            Thread.sleep(pollIntervalMillis)
        }
    }
}
