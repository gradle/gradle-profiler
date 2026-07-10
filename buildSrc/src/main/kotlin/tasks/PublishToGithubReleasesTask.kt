package tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import services.GithubReleaseService

/**
 * Publishes the CLI distribution ZIP to the project's GitHub Releases.
 *
 * The release notes are curated by release-drafter, which keeps an unpublished *draft* release
 * for the upcoming version. This task finds that draft, attaches the distribution ZIP, and flips
 * it to a published (non-draft) release so the asset is reachable at the public
 * `releases/download/...` URL that SDKMAN fetches. The actual REST/HTTP work lives in
 * [GithubReleaseService].
 */
@DisableCachingByDefault(because = "Publishes an artifact to GitHub, nothing to cache")
abstract class PublishToGithubReleasesTask : DefaultTask() {

    /** The `owner/repo` slug to publish to, e.g. `gradle/gradle-profiler`. */
    @get:Input
    abstract val repository: Property<String>

    /** The release version (without the leading `v`); the tag is `v$releaseVersion`. */
    @get:Input
    abstract val releaseVersion: Property<String>

    /** The distribution ZIP to upload as a release asset. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val distributionZip: RegularFileProperty

    @get:Internal
    abstract val githubReleaseService: Property<GithubReleaseService>

    @TaskAction
    fun publish() {
        val repo = repository.get()
        val version = releaseVersion.get()
        val tag = "v$version"
        val assetName = "gradle-profiler-$version.zip"

        githubReleaseService.get().publishReleaseAsset(repo, tag, assetName, distributionZip.get().asFile.toPath())

        logger.lifecycle("Published GitHub release $tag in $repo with asset $assetName")
    }
}
