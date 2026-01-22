package org.gradle.profiler

import org.gradle.profiler.fixtures.compatibility.gradle.AbstractGradleCrossVersionTest
import org.gradle.profiler.yourkit.YourKit
import spock.lang.Requires


class YourKitProfilerGradleCrossVersionTest extends AbstractGradleCrossVersionTest {

    def setup() {
        defaultWarmupsAndIterations()
    }

    @Requires({ YourKit.findYourKitHome() })
    def "profiles build using YourKit with tooling API and warm daemon to produce CPU tracing snapshot"() {
        given:
        instrumentedBuildScript()

        when:
        run(["--gradle-version", gradleVersion, "--profile", "yourkit", "assemble"])

        then:
        logFile.find("<daemon: true").size() == 4
        logFile.containsOne("<invocations: 3>")

        and:
        outputDir.listFiles().find { it.name.matches("${gradleVersion}-.+\\.snapshot") }
    }

    @Requires({ YourKit.findYourKitHome() })
    def "profiles build using YourKit with `gradle` command and warm daemon to produce CPU tracing snapshot"() {
        given:
        instrumentedBuildScript()

        when:
        run(["--gradle-version", gradleVersion, "--profile", "yourkit", "--cli", "assemble"])

        then:
        logFile.find("<daemon: true").size() == 4
        logFile.containsOne("<invocations: 3>")

        and:
        outputDir.listFiles().find { it.name.matches("${gradleVersion}-.+\\.snapshot") }
    }

    @Requires({ YourKit.findYourKitHome() })
    def "profiles multiple iterations using YourKit with `gradle` command and warm daemon to produce CPU tracing snapshot"() {
        given:
        instrumentedBuildScript()

        when:
        iterations = 2
        run(["--gradle-version", gradleVersion, "--profile", "yourkit", "--cli", "assemble"])

        then:
        logFile.find("<daemon: true").size() == 5
        logFile.containsOne("<invocations: 4>")

        and:
        outputDir.listFiles().find { it.name.matches("${gradleVersion}-.+\\.snapshot") }
    }

    @Requires({ YourKit.findYourKitHome() })
    def "profiles build using YourKit with tooling API and cold daemon to produce CPU tracing snapshot"() {
        given:
        instrumentedBuildScript()

        when:
        run(["--gradle-version", gradleVersion, "--cold-daemon", "--profile", "yourkit", "assemble"])

        then:
        logFile.find("<daemon: true").size() == 3
        logFile.find("<invocations: 1>").size() == 3

        and:
        outputDir.listFiles().find { it.name.matches("${gradleVersion}-.+\\.snapshot") }
    }

    @Requires({ YourKit.findYourKitHome() })
    def "profiles build using YourKit with `gradle` command and cold daemon to produce CPU tracing snapshot"() {
        given:
        instrumentedBuildScript()

        when:
        run(["--gradle-version", gradleVersion, "--cold-daemon", "--profile", "yourkit", "--cli", "assemble"])

        then:
        logFile.find("<daemon: true").size() == 3
        logFile.find("<invocations: 1>").size() == 3

        and:
        outputDir.listFiles().find { it.name.matches("${gradleVersion}-.+\\.snapshot") }
    }

    @Requires({ YourKit.findYourKitHome() })
    def "profiles build using YourKit with `gradle` command and no daemon to produce CPU tracing snapshot"() {
        given:
        instrumentedBuildScript()

        when:
        run(["--gradle-version", gradleVersion, "--profile", "yourkit", "--no-daemon", "assemble"])

        then:
        logFile.find("<daemon: true").size() == 1
        logFile.find("<daemon: false").size() == 2
        logFile.find("<invocations: 1>").size() == 3

        and:
        outputDir.listFiles().find { it.name.matches("${gradleVersion}-.+\\.snapshot") }
    }

    @Requires({ YourKit.findYourKitHome() })
    def "profiles build using YourKit with Tooling API and warm daemon to produce CPU sampling snapshot"() {
        given:
        instrumentedBuildScript()

        when:
        run(["--gradle-version", gradleVersion, "--profile", "yourkit-tracing", "assemble"])

        then:
        logFile.find("<daemon: true").size() == 4
        logFile.containsOne("<invocations: 3>")

        and:
        outputDir.listFiles().find { it.name.matches("${gradleVersion}-.+\\.snapshot") }
    }

    @Requires({ YourKit.findYourKitHome() })
    def "profiles build using YourKit with Tooling API and cold daemon to produce CPU sampling snapshot"() {
        given:
        instrumentedBuildScript()

        when:
        run(["--gradle-version", gradleVersion, "--profile", "yourkit-tracing", "--cold-daemon", "assemble"])

        then:
        logFile.find("<daemon: true").size() == 3
        logFile.find("<invocations: 1>").size() == 3

        and:
        outputDir.listFiles().find { it.name.matches("${gradleVersion}-.+\\.snapshot") }
    }

    @Requires({ YourKit.findYourKitHome() })
    def "profiles build using YourKit with `gradle` command and no daemon to produce CPU sampling snapshot"() {
        given:
        instrumentedBuildScript()

        when:
        run(["--gradle-version", gradleVersion, "--profile", "yourkit-tracing", "--no-daemon", "assemble"])

        then:
        logFile.find("<daemon: true").size() == 1
        logFile.find("<daemon: false").size() == 2
        logFile.find("<invocations: 1>").size() == 3

        and:
        outputDir.listFiles().find { it.name.matches("${gradleVersion}-.+\\.snapshot") }
    }

    @Requires({ YourKit.findYourKitHome() })
    def "profiles build using YourKit with Tooling API and warm daemon to produce memory snapshot"() {
        given:
        instrumentedBuildScript()

        when:
        run(["--gradle-version", gradleVersion, "--profile", "yourkit-heap", "assemble"])

        then:
        logFile.find("<daemon: true").size() == 4
        logFile.containsOne("<invocations: 3>")

        and:
        outputDir.listFiles().find { it.name.matches("${gradleVersion}-.+\\.snapshot") }
    }

    @Requires({ YourKit.findYourKitHome() })
    def "profiles build using YourKit with Tooling API and cold daemon to produce memory snapshot"() {
        given:
        instrumentedBuildScript()

        when:
        run(["--gradle-version", gradleVersion, "--profile", "yourkit-heap", "--cold-daemon", "assemble"])

        then:
        logFile.find("<daemon: true").size() == 3
        logFile.find("<invocations: 1>").size() == 3

        and:
        outputDir.listFiles().find { it.name.matches("${gradleVersion}-.+\\.snapshot") }
    }

    @Requires({ YourKit.findYourKitHome() })
    def "profiles build using YourKit with `gradle` command and no daemon to produce memory snapshot"() {
        given:
        instrumentedBuildScript()

        when:
        run(["--gradle-version", gradleVersion, "--profile", "yourkit-heap", "--no-daemon", "assemble"])

        then:
        logFile.find("<daemon: true").size() == 1
        logFile.find("<daemon: false").size() == 2
        logFile.find("<invocations: 1>").size() == 3

        and:
        outputDir.listFiles().find { it.name.matches("${gradleVersion}-.+\\.snapshot") }
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
        iterations = 2
        run(["--gradle-version", gradleVersion, "--scenario-file", scenarioFile.absolutePath, "--profile", "yourkit", "assemble"])

        then:
        thrown(IllegalArgumentException)

        and:
        output.contains("Scenario assemble using Gradle ${gradleVersion}: Profiler YourKit does not support profiling multiple iterations with cleanup steps in between.")
    }

    def "cannot profile using YourKit with multiple iterations and cold daemon"() {
        given:
        instrumentedBuildScript()

        when:
        iterations = 2
        run(["--gradle-version", gradleVersion, "--profile", "yourkit", "--cold-daemon", "assemble"])

        then:
        thrown(IllegalArgumentException)

        and:
        output.contains("Scenario using Gradle ${gradleVersion}: Profiler YourKit does not support profiling multiple daemons.")
    }

    def "cannot profile using YourKit with multiple iterations and no daemon"() {
        given:
        instrumentedBuildScript()

        when:
        iterations = 2
        run(["--gradle-version", gradleVersion, "--profile", "yourkit", "--no-daemon", "assemble"])

        then:
        thrown(IllegalArgumentException)

        and:
        output.contains("Scenario using Gradle ${gradleVersion}: Profiler YourKit does not support profiling multiple daemons.")
    }
}
