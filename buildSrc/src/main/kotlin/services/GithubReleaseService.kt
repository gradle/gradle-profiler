package services

import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path

/**
 * Encapsulates the GitHub REST calls needed to publish a release asset, hiding the [HttpClient]
 * and auth handling from the tasks that use it.
 */
abstract class GithubReleaseService : BuildService<GithubReleaseService.Params> {

    interface Params : BuildServiceParameters {
        /** GitHub token with `contents: write` on the target repository. */
        val githubToken: Property<String>
    }

    private val token: String
        get() = parameters.githubToken.orNull
            ?: throw GradleException("The 'githubToken' property is required to publish to GitHub Releases.")

    private val client: HttpClient = HttpClient.newHttpClient()

    /**
     * Attaches [asset] (named [assetName]) to the release-drafter draft tagged [tag] in [repo] and
     * flips that draft to a published, latest release. Idempotent: a pre-existing asset with the
     * same name is replaced. Fails loudly if no matching draft exists or any call is non-2xx.
     */
    fun publishReleaseAsset(repo: String, tag: String, assetName: String, asset: Path) {
        val (releaseId, existingAssets) = findDraftRelease(repo, tag)
        existingAssets
            .filter { it["name"] == assetName }
            .forEach { deleteAsset(repo, (it["id"] as Number).toLong()) }
        uploadAsset(repo, releaseId, assetName, asset)
        publishRelease(repo, releaseId)
    }

    private fun findDraftRelease(repo: String, tag: String): Pair<Long, List<Map<String, Any?>>> {
        val response = send(
            authorizedRequest("https://api.github.com/repos/$repo/releases?per_page=100").GET().build(),
            "Listing releases"
        )
        @Suppress("UNCHECKED_CAST")
        val releases = JsonSlurper().parseText(response.body()) as List<Map<String, Any?>>
        val release = releases.firstOrNull { it["tag_name"] == tag && it["draft"] == true }
            ?: throw GradleException("No draft release found with tag '$tag' in $repo. release-drafter should have created one before publishing.")
        @Suppress("UNCHECKED_CAST")
        val assets = release["assets"] as? List<Map<String, Any?>> ?: emptyList()
        return (release["id"] as Number).toLong() to assets
    }

    private fun deleteAsset(repo: String, assetId: Long) {
        send(
            authorizedRequest("https://api.github.com/repos/$repo/releases/assets/$assetId").DELETE().build(),
            "Deleting existing asset $assetId"
        )
    }

    private fun uploadAsset(repo: String, releaseId: Long, assetName: String, asset: Path) {
        send(
            authorizedRequest("https://uploads.github.com/repos/$repo/releases/$releaseId/assets?name=$assetName")
                .header("Content-Type", "application/zip")
                .POST(HttpRequest.BodyPublishers.ofFile(asset))
                .build(),
            "Uploading asset $assetName"
        )
    }

    private fun publishRelease(repo: String, releaseId: Long) {
        send(
            authorizedRequest("https://api.github.com/repos/$repo/releases/$releaseId")
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString("""{"draft": false, "make_latest": "true"}"""))
                .build(),
            "Publishing release $releaseId"
        )
    }

    private fun authorizedRequest(uri: String) = HttpRequest.newBuilder(URI.create(uri))
        .header("Authorization", "Bearer $token")
        .header("Accept", "application/vnd.github+json")

    private fun send(request: HttpRequest, description: String): HttpResponse<String> {
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw GradleException("$description failed: HTTP ${response.statusCode()}\n${response.body()}")
        }
        return response
    }
}
