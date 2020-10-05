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
* All done!
