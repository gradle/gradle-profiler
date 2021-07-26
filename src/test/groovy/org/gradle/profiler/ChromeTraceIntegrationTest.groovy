package org.gradle.profiler

import org.gradle.util.GradleVersion
import spock.lang.Shared
import spock.lang.Unroll


class ChromeTraceIntegrationTest extends AbstractProfilerIntegrationTest {
    static final MINIMAL_SUPPORTED_VERSION_FOR_CHROME_TRACE = GradleVersion.version("3.5")

    @Shared
    List<String> supportedGradleVersions = super.supportedGradleVersions.findAll { gradleVersion ->
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
        new File(outputDir, "${versionUnderTest}-trace.json").isFile()

        where:
        versionUnderTest << supportedGradleVersions
    }

    @Unroll
    def "profiles build to produce chrome trace output for Gradle #versionUnderTest using Tooling API and cold daemon"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().
            run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", versionUnderTest, "--profile", "chrome-trace", "--cold-daemon", "assemble")

        then:
        new File(outputDir, "${versionUnderTest}-trace.json").isFile()

        where:
        versionUnderTest << supportedGradleVersions
    }

    @Unroll
    def "profiles build to produce chrome trace output for Gradle #versionUnderTest using `gradle` command and no daemon"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().
            run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", versionUnderTest, "--profile", "chrome-trace", "--no-daemon", "assemble")

        then:
        new File(outputDir, "${versionUnderTest}-trace.json").isFile()

        where:
        versionUnderTest << supportedGradleVersions
    }
}
