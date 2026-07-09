package tasks

import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Publishes the CLI distribution ZIP to the project's GitHub Releases.
 *
 * The release notes are curated by release-drafter, which keeps an unpublished *draft* release
 * for the upcoming version. This task finds that draft, attaches the distribution ZIP, and flips
 * it to a published (non-draft) release so the asset is reachable at the public
 * `releases/download/...` URL that SDKMAN fetches.
 */
@DisableCachingByDefault(because = "Publishes an artifact to GitHub, nothing to cache")
abstract class PublishToGithubReleasesTask : DefaultTask() {

    /** The `owner/repo` slug to publish to, e.g. `gradle/gradle-profiler`. */
    @get:Input
    abstract val repository: Property<String>

    /** The release version (without the leading `v`); the tag is `v$releaseVersion`. */
    @get:Input
    abstract val releaseVersion: Property<String>

    /** GitHub token with `contents: write` on [repository]. */
    @get:Internal
    abstract val githubToken: Property<String>

    /** The distribution ZIP to upload as a release asset. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val distributionZip: RegularFileProperty

    @TaskAction
    fun publish() {
        val repo = repository.get()
        val version = releaseVersion.get()
        val token = githubToken.orNull
            ?: throw GradleException("The 'githubToken' property is required to publish to GitHub Releases.")
        val tag = "v$version"
        val assetName = "gradle-profiler-$version.zip"
        val apiBase = "https://api.github.com/repos/$repo"
        val client = HttpClient.newHttpClient()

        fun authorizedRequest(uri: String) = HttpRequest.newBuilder(URI.create(uri))
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")

        fun send(request: HttpRequest, description: String): HttpResponse<String> {
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                throw GradleException("$description failed: HTTP ${response.statusCode()}\n${response.body()}")
            }
            return response
        }

        // (a) Find the drafted release for this tag.
        val listResponse = send(
            authorizedRequest("$apiBase/releases?per_page=100").GET().build(),
            "Listing releases"
        )
        @Suppress("UNCHECKED_CAST")
        val releases = JsonSlurper().parseText(listResponse.body()) as List<Map<String, Any?>>
        val release = releases.firstOrNull { it["tag_name"] == tag && it["draft"] == true }
            ?: throw GradleException("No draft release found with tag '$tag' in $repo. release-drafter should have created one before publishing.")
        val releaseId = (release["id"] as Number).toLong()

        // (b) Delete any pre-existing asset with the same name so re-runs are idempotent.
        @Suppress("UNCHECKED_CAST")
        val assets = release["assets"] as? List<Map<String, Any?>> ?: emptyList()
        assets.filter { it["name"] == assetName }.forEach { asset ->
            val assetId = (asset["id"] as Number).toLong()
            send(
                authorizedRequest("$apiBase/releases/assets/$assetId").DELETE().build(),
                "Deleting existing asset $assetId"
            )
        }

        // (c) Upload the distribution ZIP.
        val zipPath = distributionZip.get().asFile.toPath()
        send(
            authorizedRequest("https://uploads.github.com/repos/$repo/releases/$releaseId/assets?name=$assetName")
                .header("Content-Type", "application/zip")
                .POST(HttpRequest.BodyPublishers.ofFile(zipPath))
                .build(),
            "Uploading asset $assetName"
        )

        // (d) Publish the release (flip draft -> published, mark as latest).
        send(
            authorizedRequest("$apiBase/releases/$releaseId")
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString("""{"draft": false, "make_latest": "true"}"""))
                .build(),
            "Publishing release $releaseId"
        )

        logger.lifecycle("Published GitHub release $tag in $repo with asset $assetName")
    }
}
