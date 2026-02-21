package org.gradle.profiler

import org.gradle.profiler.gradle.GradleBuildInvoker
import org.gradle.profiler.report.Format
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class DumpScenariosTest extends Specification {
    @Rule
    TemporaryFolder tmpDir = new TemporaryFolder()

    File projectDir
    File gradleUserHomeDir
    File outputDir
    File scenarioFile

    def setup() {
        projectDir = tmpDir.newFolder()
        outputDir = tmpDir.newFolder()
        scenarioFile = tmpDir.newFile()
    }

    private settingsBuilder(
        BuildInvoker invoker = GradleBuildInvoker.Cli,
        boolean benchmark = true
    ) {
        new InvocationSettings.InvocationSettingsBuilder()
            .setProjectDir(projectDir)
            .setProfiler(Profiler.NONE)
            .setBenchmark(benchmark)
            .setOutputDir(outputDir)
            .setInvoker(invoker)
            .setDryRun(false)
            .setScenarioFile(scenarioFile)
            .setVersions([])
            .setTargets([])
            .setSysProperties([:])
            .setGradleUserHome(gradleUserHomeDir)
            .setIdeInstallDir(tmpDir.newFolder())
            .setMeasureGarbageCollection(false)
            .setMeasureConfigTime(false)
            .setBuildOperationMeasurements([])
            .setCsvFormat(Format.WIDE)
    }

    private settings(
        BuildInvoker invoker = GradleBuildInvoker.Cli,
        boolean benchmark = true
    ) {
        settingsBuilder(invoker, benchmark)
            .build()
    }

    def "dumpScenarios returns formatted output for single scenario"() {
        def settings = settings()

        scenarioFile << """
            default {
                tasks = ["help"]
                warm-ups = 5
            }
        """

        when:
        def output = ScenarioLoader.dumpScenarios(scenarioFile, settings)

        then:
        output == """
# Scenario 1/1
default {
    tasks=[
        help
    ]
    warm-ups=5
}
"""
    }

    def "dumpScenarios returns resolved config"() {
        def settings = settings()

        scenarioFile << """
            default {
                daemon = cold
                iterations = 10
                tasks = ["assemble"]
                system-properties {
                    "prop1" = "value1"
                    "prop2" = "value2"
                }
            }

            # HOCON allows duplicate object declarations, in which case they are merged
            default {
                # primitive values are overridden
                iterations = 33
                # arrays are also overridden
                tasks = ["build"]
                # nested objects are merged
                system-properties {
                    "prop2" = "overridden"
                    "prop3" = "value3"
                }
            }
        """

        when:
        def output = ScenarioLoader.dumpScenarios(scenarioFile, settings)

        then:
        output == """
# Scenario 1/1
default {
    daemon=cold
    iterations=33
    system-properties {
        prop1=value1
        prop2=overridden
        prop3=value3
    }
    tasks=[
        build
    ]
}
"""
    }

    def "dumpScenarios returns output for multiple scenarios with explicit targets"() {
        def settings = settingsBuilder()
            .setTargets(["scenario1", "scenario2"])
            .build()

        scenarioFile << """
            scenario1 {
                tasks = ["clean", "assemble"]
                warm-ups = 2
            }
            scenario2 {
                tasks = ["build"]
                iterations = 5
            }
            scenario3 {
                tasks = ["test"]
            }
        """

        when:
        def output = ScenarioLoader.dumpScenarios(scenarioFile, settings)

        then:
        output == """
# Scenario 1/2
scenario1 {
    tasks=[
        clean,
        assemble
    ]
    warm-ups=2
}

# Scenario 2/2
scenario2 {
    iterations=5
    tasks=[
        build
    ]
}
"""
    }

    def "dumpScenarios returns default scenarios when no targets specified"() {
        def settings = settings()

        scenarioFile << """
            default-scenarios = ["scenario1", "scenario2"]

            scenario1 {
                tasks = ["assemble"]
                daemon = warm
            }
            scenario2 {
                tasks = ["build"]
                iterations = 10
            }
            scenario3 {
                tasks = ["test"]
            }
        """

        when:
        def output = ScenarioLoader.dumpScenarios(scenarioFile, settings)

        then:
        output == """
# Scenario 1/2
scenario1 {
    daemon=warm
    tasks=[
        assemble
    ]
}

# Scenario 2/2
scenario2 {
    iterations=10
    tasks=[
        build
    ]
}
"""
    }

    def "dumpScenarios returns scenarios from a group"() {
        def settings = settingsBuilder()
            .setScenarioGroup("smoke-tests")
            .build()

        scenarioFile << """
            scenario-groups {
                smoke-tests = ["quick-build", "quick-test"]
                full-suite = ["full-build"]
            }

            quick-build {
                tasks = ["assemble"]
                warm-ups = 1
            }
            quick-test {
                tasks = ["test"]
                iterations = 3
            }
            full-build {
                tasks = ["clean", "build"]
                warm-ups = 5
            }
        """

        when:
        def output = ScenarioLoader.dumpScenarios(scenarioFile, settings)

        then:
        output == """
# Scenario 1/2
quick-build {
    tasks=[
        assemble
    ]
    warm-ups=1
}

# Scenario 2/2
quick-test {
    iterations=3
    tasks=[
        test
    ]
}
"""
    }

    def "dumpScenarios includes scenario title in header when present"() {
        def settings = settingsBuilder()
            .setTargets(["with-title", "without-title"])
            .build()

        scenarioFile << """
            with-title {
                title = "Custom title"
                tasks = ["build"]
                warm-ups = 5
            }
            without-title {
                tasks = ["assemble"]
            }
        """

        when:
        def output = ScenarioLoader.dumpScenarios(scenarioFile, settings)

        then:
        output == """
# Scenario 1/2 'Custom title'
with-title {
    tasks=[
        build
    ]
    title="Custom title"
    warm-ups=5
}

# Scenario 2/2
without-title {
    tasks=[
        assemble
    ]
}
"""
    }
}
