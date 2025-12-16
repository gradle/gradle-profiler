package org.gradle.profiler


import org.gradle.profiler.fixtures.compatibility.gradle.AbstractGradleCrossVersionTest
import org.gradle.profiler.spock.extensions.ShowAndroidStudioLogsOnFailure
import org.gradle.profiler.studio.AndroidStudioTestSupport
import org.gradle.profiler.studio.tools.StudioFinder
import spock.lang.Requires

import static org.gradle.profiler.studio.AndroidStudioTestSupport.setupLocalProperties

class ChromeTraceGradleCrossVersionTest extends AbstractGradleCrossVersionTest {

    File sandboxDir

    def setup() {
        sandboxDir = tmpDir.newFolder('sandbox')
    }

    def "profiles build to produce chrome trace output using #conditions"() {
        given:
        instrumentedBuildScript()

        when:
        run(["--gradle-version", gradleVersion, "--profile", "chrome-trace", "assemble", *args])

        then:
        new File(outputDir, "${gradleVersion}-trace/${gradleVersion}-warm-up-build-1-invocation-1-trace.json").isFile()
        if (conditions.contains("warm daemon")) {
            new File(outputDir, "${gradleVersion}-trace/${gradleVersion}-warm-up-build-2-invocation-1-trace.json").isFile()
        }
        new File(outputDir, "${gradleVersion}-trace/${gradleVersion}-measured-build-1-invocation-1-trace.json").isFile()

        where:
        conditions                       | args
        "Tooling API and cold daemon"    | ["--cold-daemon"]
        "Tooling API and warm daemon"    | []
        "`gradle` command and no daemon" | ["--no-daemon"] // --no-daemon implies --cli
    }

    @ShowAndroidStudioLogsOnFailure
    @Requires({ StudioFinder.findStudioHome() })
    @Requires({ AndroidStudioTestSupport.findAndroidSdkPath() })
    // We don't control version of AS provided by the environment, so we assume that latest tested Gradle should be fine
    @Requires({ it.instance.latestTestedGradleVersion() })
    def "profiles Android Studio build to produce chrome trace output for builds"() {
        given:
        def studioHome = StudioFinder.findStudioHome()
        setupLocalProperties(new File(projectDir, "local.properties"))
        new File(projectDir, "buildSrc").mkdirs()
        new File(projectDir, "buildSrc/gradle.build").createNewFile()
        def scenarioFile = file("performance.scenarios") << """
            scenario {
                android-studio-sync {}
            }
        """

        when:
        run(["--gradle-version", gradleVersion,
             "--profile", "chrome-trace",
             "--scenario-file", scenarioFile.absolutePath,
             "--studio-install-dir", studioHome.absolutePath,
             "--studio-sandbox-dir", sandboxDir.absolutePath])

        then:
        new File(outputDir, "scenario-${gradleVersion}-trace/scenario-${gradleVersion}-warm-up-build-1-invocation-1-trace.json").isFile()
        new File(outputDir, "scenario-${gradleVersion}-trace/scenario-${gradleVersion}-measured-build-1-invocation-1-trace.json").isFile()
    }
}
