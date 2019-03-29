package org.gradle.profiler

import org.gradle.profiler.yourkit.YourKit
import spock.lang.Requires


class YourKitProfilerIntegrationTest extends AbstractProfilerIntegrationTest {
    @Requires({ YourKit.findYourKitHome() })
    def "profiles build using YourKit with tooling API and warm daemon to produce CPU tracing snapshot"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--profile", "yourkit", "assemble")

        then:
        logFile.find("<daemon: true").size() == 4
        logFile.containsOne("<invocations: 3>")

        and:
        outputDir.listFiles().find { it.name.matches("${minimalSupportedGradleVersion}-.+\\.snapshot") }
    }

    @Requires({ YourKit.findYourKitHome() })
    def "profiles build using YourKit with `gradle` command and warm daemon to produce CPU tracing snapshot"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--profile", "yourkit", "--cli", "assemble")

        then:
        logFile.find("<daemon: true").size() == 4
        logFile.containsOne("<invocations: 3>")

        and:
        outputDir.listFiles().find { it.name.matches("${minimalSupportedGradleVersion}-.+\\.snapshot") }
    }

    @Requires({ YourKit.findYourKitHome() })
    def "profiles multiple iterations using YourKit with `gradle` command and warm daemon to produce CPU tracing snapshot"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--profile", "yourkit", "--iterations", "2", "--cli", "assemble")

        then:
        logFile.find("<daemon: true").size() == 5
        logFile.containsOne("<invocations: 4>")

        and:
        outputDir.listFiles().find { it.name.matches("${minimalSupportedGradleVersion}-.+\\.snapshot") }
    }

    @Requires({ YourKit.findYourKitHome() })
    def "profiles build using YourKit with tooling API and cold daemon to produce CPU tracing snapshot"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--cold-daemon", "--profile", "yourkit", "assemble")

        then:
        logFile.find("<daemon: true").size() == 3
        logFile.find("<invocations: 1>").size() == 3

        and:
        outputDir.listFiles().find { it.name.matches("${minimalSupportedGradleVersion}-.+\\.snapshot") }
    }

    @Requires({ YourKit.findYourKitHome() })
    def "profiles build using YourKit with `gradle` command and cold daemon to produce CPU tracing snapshot"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--cold-daemon", "--profile", "yourkit", "--cli", "assemble")

        then:
        logFile.find("<daemon: true").size() == 3
        logFile.find("<invocations: 1>").size() == 3

        and:
        outputDir.listFiles().find { it.name.matches("${minimalSupportedGradleVersion}-.+\\.snapshot") }
    }

    @Requires({ YourKit.findYourKitHome() })
    def "profiles build using YourKit with `gradle` command and no daemon to produce CPU tracing snapshot"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--profile", "yourkit", "--no-daemon", "assemble")

        then:
        logFile.find("<daemon: true").size() == 1
        logFile.find("<daemon: false").size() == 2
        logFile.find("<invocations: 1>").size() == 3

        and:
        outputDir.listFiles().find { it.name.matches("${minimalSupportedGradleVersion}-.+\\.snapshot") }
    }

    @Requires({ YourKit.findYourKitHome() })
    def "profiles build using YourKit with Tooling API and warm daemon to produce CPU sampling snapshot"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--profile", "yourkit", "--yourkit-sampling", "assemble")

        then:
        logFile.find("<daemon: true").size() == 4
        logFile.containsOne("<invocations: 3>")

        and:
        outputDir.listFiles().find { it.name.matches("${minimalSupportedGradleVersion}-.+\\.snapshot") }
    }

    @Requires({ YourKit.findYourKitHome() })
    def "profiles build using YourKit with Tooling API and cold daemon to produce CPU sampling snapshot"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--profile", "yourkit", "--cold-daemon", "--yourkit-sampling", "assemble")

        then:
        logFile.find("<daemon: true").size() == 3
        logFile.find("<invocations: 1>").size() == 3

        and:
        outputDir.listFiles().find { it.name.matches("${minimalSupportedGradleVersion}-.+\\.snapshot") }
    }

    @Requires({ YourKit.findYourKitHome() })
    def "profiles build using YourKit with `gradle` command and no daemon to produce CPU sampling snapshot"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--profile", "yourkit", "--yourkit-sampling", "--no-daemon", "assemble")

        then:
        logFile.find("<daemon: true").size() == 1
        logFile.find("<daemon: false").size() == 2
        logFile.find("<invocations: 1>").size() == 3

        and:
        outputDir.listFiles().find { it.name.matches("${minimalSupportedGradleVersion}-.+\\.snapshot") }
    }

    @Requires({ YourKit.findYourKitHome() })
    def "profiles build using YourKit with Tooling API and warm daemon to produce memory snapshot"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--profile", "yourkit", "--yourkit-memory", "assemble")

        then:
        logFile.find("<daemon: true").size() == 4
        logFile.containsOne("<invocations: 3>")

        and:
        outputDir.listFiles().find { it.name.matches("${minimalSupportedGradleVersion}-.+\\.snapshot") }
    }

    @Requires({ YourKit.findYourKitHome() })
    def "profiles build using YourKit with Tooling API and cold daemon to produce memory snapshot"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--profile", "yourkit", "--yourkit-memory", "--cold-daemon", "assemble")

        then:
        logFile.find("<daemon: true").size() == 3
        logFile.find("<invocations: 1>").size() == 3

        and:
        outputDir.listFiles().find { it.name.matches("${minimalSupportedGradleVersion}-.+\\.snapshot") }
    }

    @Requires({ YourKit.findYourKitHome() })
    def "profiles build using YourKit with `gradle` command and no daemon to produce memory snapshot"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--profile", "yourkit", "--yourkit-memory", "--no-daemon", "assemble")

        then:
        logFile.find("<daemon: true").size() == 1
        logFile.find("<daemon: false").size() == 2
        logFile.find("<invocations: 1>").size() == 3

        and:
        outputDir.listFiles().find { it.name.matches("${minimalSupportedGradleVersion}-.+\\.snapshot") }
    }

    def "cannot profile using YourKit with multiple iterations and cleanup steps"() {
        given:
        instrumentedBuildScript()

        def scenarioFile = file("performance.scenarios")
        scenarioFile.text = """
            assemble {
                cleanup-tasks = "clean"
            }
        """

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--scenario-file", scenarioFile.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--profile", "yourkit", "--iterations", "2", "assemble")

        then:
        thrown(IllegalArgumentException)

        and:
        output.contains("Scenario assemble using Gradle ${minimalSupportedGradleVersion}: Profiler YourKit does not support profiling multiple iterations with cleanup steps in between.")
    }

    def "cannot profile using YourKit with multiple iterations and cold daemon"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--profile", "yourkit", "--iterations", "2", "--cold-daemon", "assemble")

        then:
        thrown(IllegalArgumentException)

        and:
        output.contains("Scenario using Gradle ${minimalSupportedGradleVersion}: Profiler YourKit does not support profiling multiple daemons.")
    }

    def "cannot profile using YourKit with multiple iterations and no daemon"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--profile", "yourkit", "--iterations", "2", "--no-daemon", "assemble")

        then:
        thrown(IllegalArgumentException)

        and:
        output.contains("Scenario using Gradle ${minimalSupportedGradleVersion}: Profiler YourKit does not support profiling multiple daemons.")
    }
}
