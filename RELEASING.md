### Release Process

These are the steps to release Gradle profiler.

* Edit gradle.properties to strip -SNAPSHOT from `profiler.version`
* Ensure everything is checked in: _git commit -S -am "Prepare releasing 0.12.0"_
* Push your changes
* Publish the version by using [this Teamcity build](https://builds.gradle.org/buildConfiguration/GradleProfiler_GradleProfilerPublishing?branch=%3Cdefault%3E).
  The build will upload the artifacts, add and push a tag and add the release to SDKMAN.
  If the version is a final version (it only contains numbers and dots), then it is announced and set as the default version on SDKMAN as well.
* Increment the version in gradle.properties and append "-SNAPSHOT": _echo "profiler.version=0.13.0-SNAPSHOT">gradle.properties_
* Commit the updated version number: _git commit -S -am "Prepare next development version 0.13.0"_
* Push the version update
* Update the [release draft](https://github.com/gradle/gradle-profiler/releases) prepared by [release-drafter](https://probot.github.io/apps/release-drafter/), assign the tag you created and publish the release
* The [Publish to SDKman](https://builds.gradle.org/buildConfiguration/GradleProfiler_GradleProfilerPublishToSdkMan?branch=%3Cdefault%3E) should trigger automatically after the publishing job finishes. Though it may fail, since the artifacts are not yet on Maven Central. The build will retry the SDKMan publishing after half an hour. If it still doesn't work, you can trigger the build manually. 
* All done!

### Releasing Pre-release Versions

Releasing pre-release versions from the master branch is also supported.
These versions are normally used for dogfooding in the [gradle/gradle](https://github.com/gradle/gradle) build.
We use `-alpha-<number>` suffix for these versions, e.g. `0.21.0-alpha-1`.

Process is the same as releasing the final versions, except that you should not publish the [release draft](https://github.com/gradle/gradle-profiler/releases).
Also, the build will automatically detect a snapshot version if it contains `snapshot` or `alpha` and will skip publishing to SDKMan.

Latest version can be found in [Maven Central](https://mvnrepository.com/artifact/org.gradle.profiler/gradle-profiler).

