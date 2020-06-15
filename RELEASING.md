These are the steps to release Gradle profiler.

* Edit gradle.properties to strip -SNAPSHOT from `profiler.version`
* Ensure everything is checked in: _git commit -S -am "Prepare releasing 0.12.0"_
* Push your changes
* Publish the version by using [this Teamcity build](https://builds.gradle.org/buildConfiguration/GradleProfiler_GradleProfilerPublishing?branch=%3Cdefault%3E)
* If the build is successful, tag the source:  _git tag -s -a v0.12.0 -m "Staging 0.12.0"_ 
* Push the tag back to GitHub: _git push --tags_
* Increment the version in gradle.properties and append "-SNAPSHOT": _echo "profiler.version=0.13.0-SNAPSHOT">gradle.properties_
* Commit the updated version number: _git commit -S -am "Prepare next development version 0.13.0"_
* Push the version update
* Update the [release draft](https://github.com/gradle/gradle-profiler/releases) prepared by [release-drafter](https://probot.github.io/apps/release-drafter/), assign the tag you created and publish the release
* All done!
