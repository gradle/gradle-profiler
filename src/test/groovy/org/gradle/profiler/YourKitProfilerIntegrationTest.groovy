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
        logFile.grep("<daemon: true").size() == 4
        logFile.contains("<invocations: 3>")

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
        logFile.grep("<daemon: true").size() == 4
        logFile.contains("<invocations: 3>")

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
        logFile.grep("<daemon: true").size() == 3
        logFile.grep("<invocations: 1>").size() == 3

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
        logFile.grep("<daemon: true").size() == 3
        logFile.grep("<invocations: 1>").size() == 3

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
        logFile.grep("<daemon: true").size() == 1
        logFile.grep("<daemon: false").size() == 2
        logFile.grep("<invocations: 1>").size() == 3

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
        logFile.grep("<daemon: true").size() == 4
        logFile.contains("<invocations: 3>")

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
        logFile.grep("<daemon: true").size() == 3
        logFile.grep("<invocations: 1>").size() == 3

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
        logFile.grep("<daemon: true").size() == 1
        logFile.grep("<daemon: false").size() == 2
        logFile.grep("<invocations: 1>").size() == 3

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
        logFile.grep("<daemon: true").size() == 4
        logFile.contains("<invocations: 3>")

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
        logFile.grep("<daemon: true").size() == 3
        logFile.grep("<invocations: 1>").size() == 3

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
        logFile.grep("<daemon: true").size() == 1
        logFile.grep("<daemon: false").size() == 2
        logFile.grep("<invocations: 1>").size() == 3

        and:
        outputDir.listFiles().find { it.name.matches("${minimalSupportedGradleVersion}-.+\\.snapshot") }
    }
}
