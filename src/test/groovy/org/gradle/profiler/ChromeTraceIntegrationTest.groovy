package org.gradle.profiler

import org.gradle.profiler.spock.extensions.ShowAndroidStudioLogsOnFailure
import org.gradle.profiler.studio.AndroidStudioTestSupport
import org.gradle.profiler.studio.tools.StudioFinder
import org.gradle.util.GradleVersion
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Unroll

import static org.gradle.profiler.studio.AndroidStudioTestSupport.setupLocalProperties

class ChromeTraceIntegrationTest extends AbstractProfilerIntegrationTest {

    static final MINIMAL_SUPPORTED_VERSION_FOR_CHROME_TRACE = GradleVersion.version("3.5")

    File sandboxDir

    def setup() {
        sandboxDir = tmpDir.newFolder('sandbox')
    }

    @Shared
    List<String> supportedGradleVersionsForChromeTrace = super.supportedGradleVersions.findAll { gradleVersion ->
        GradleVersion.version(gradleVersion) >= MINIMAL_SUPPORTED_VERSION_FOR_CHROME_TRACE
    }

    @Unroll
    def "profiles build to produce chrome trace output for Gradle #versionUnderTest using Tooling API and warm daemon"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().
            run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", versionUnderTest, "--profile", "chrome-trace", "assemble")

        then:
        new File(outputDir, "${versionUnderTest}-trace/${versionUnderTest}-warm-up-build-1-invocation-1-trace.json").isFile()
        new File(outputDir, "${versionUnderTest}-trace/${versionUnderTest}-warm-up-build-2-invocation-1-trace.json").isFile()
        new File(outputDir, "${versionUnderTest}-trace/${versionUnderTest}-measured-build-1-invocation-1-trace.json").isFile()

        where:
        versionUnderTest << supportedGradleVersionsForChromeTrace
    }

    @Unroll
    def "profiles build to produce chrome trace output for Gradle #versionUnderTest using Tooling API and cold daemon"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().
            run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", versionUnderTest, "--profile", "chrome-trace", "--cold-daemon", "assemble")

        then:
        new File(outputDir, "${versionUnderTest}-trace/${versionUnderTest}-warm-up-build-1-invocation-1-trace.json").isFile()
        new File(outputDir, "${versionUnderTest}-trace/${versionUnderTest}-measured-build-1-invocation-1-trace.json").isFile()

        where:
        versionUnderTest << supportedGradleVersionsForChromeTrace
    }

    @Unroll
    def "profiles build to produce chrome trace output for Gradle #versionUnderTest using `gradle` command and no daemon"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().
            run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", versionUnderTest, "--profile", "chrome-trace", "--no-daemon", "assemble")

        then:
        new File(outputDir, "${versionUnderTest}-trace/${versionUnderTest}-warm-up-build-1-invocation-1-trace.json").isFile()
        new File(outputDir, "${versionUnderTest}-trace/${versionUnderTest}-measured-build-1-invocation-1-trace.json").isFile()

        where:
        versionUnderTest << supportedGradleVersionsForChromeTrace
    }

    @ShowAndroidStudioLogsOnFailure
    @Requires({ StudioFinder.findStudioHome() })
    @Requires({ AndroidStudioTestSupport.findAndroidSdkPath() })
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
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath,
            "--gradle-version", latestSupportedGradleVersion,
            "--profile", "chrome-trace",
            "--scenario-file", scenarioFile.absolutePath,
            "--studio-install-dir", studioHome.absolutePath,
            "--studio-sandbox-dir", sandboxDir.absolutePath,
            "--warmups", "1",
            "--iterations", "1")

        then:
        new File(outputDir, "scenario-${latestSupportedGradleVersion}-trace/scenario-${latestSupportedGradleVersion}-warm-up-build-1-invocation-1-trace.json").isFile()
        new File(outputDir, "scenario-${latestSupportedGradleVersion}-trace/scenario-${latestSupportedGradleVersion}-measured-build-1-invocation-1-trace.json").isFile()
    }
}
