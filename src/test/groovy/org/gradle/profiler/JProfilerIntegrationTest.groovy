package org.gradle.profiler

import org.gradle.profiler.jprofiler.JProfiler
import spock.lang.Requires


class JProfilerIntegrationTest extends AbstractProfilerIntegrationTest {
    @Requires({ new File(JProfiler.getDefaultHomeDir()).exists() })
    def "profiles build using JProfiler with tooling API and warm daemon"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--profile", "jprofiler", "assemble")

        then:
        logFile.find("<daemon: true").size() == 4
        logFile.containsOne("<invocations: 3>")

        and:
        outputDir.listFiles().find { it.name.matches("${minimalSupportedGradleVersion}.jps") }
    }

    @Requires({ new File(JProfiler.getDefaultHomeDir()).exists() })
    def "profiles build using JProfiler with multiple iterations and cleanup steps"() {
        given:
        instrumentedBuildScript()

        def scenarioFile = file("performance.scenarios")
        scenarioFile.text = """
            assemble {
                cleanup-tasks = "clean"
            }
        """

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--scenario-file", scenarioFile.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--profile", "jprofiler", "--iterations", "2", "assemble")

        then:
        logFile.find("<daemon: true").size() == 9
        logFile.find("<tasks: [clean]").size() == 4
        logFile.find("<tasks: []").size() == 4
        logFile.containsOne("<invocations: 8>")

        and:
        new File(outputDir, "assemble").listFiles().find { it.name.matches("assemble-${minimalSupportedGradleVersion}.jps") }
    }

    @Requires({ new File(JProfiler.getDefaultHomeDir()).exists() })
    def "profiles build using JProfiler with tooling API and cold daemon"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--profile", "jprofiler", "--cold-daemon", "assemble")

        then:
        logFile.find("<daemon: true").size() == 3
        logFile.find("<invocations: 1>").size() == 3

        and:
        outputDir.listFiles().find { it.name.matches("${minimalSupportedGradleVersion}.jps") }
    }

    @Requires({ new File(JProfiler.getDefaultHomeDir()).exists() })
    def "profiles build using JProfile with `gradle` command and no daemon"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--profile", "jprofiler", "--no-daemon", "assemble")

        then:
        logFile.find("<daemon: true").size() == 1
        logFile.find("<daemon: false").size() == 2
        logFile.find("<invocations: 1>").size() == 3

        and:
        outputDir.listFiles().find { it.name.matches("${minimalSupportedGradleVersion}.jps") }
    }

    @Requires({ new File(JProfiler.getDefaultHomeDir()).exists() })
    def "profiles build using JProfiler with all supported options"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--profile", "jprofiler", "--jprofiler-config", "instrumentation", "--jprofiler-alloc", "--jprofiler-monitors", "--jprofiler-heapdump", "--jprofiler-probes", "builtin.FileProbe,builtin.ClassLoaderProbe:+events",
                "assemble")

        then:
        outputDir.listFiles().find { it.name.matches("${minimalSupportedGradleVersion}.jps") }
    }
}
