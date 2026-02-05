package org.gradle.profiler

import org.gradle.profiler.bazel.BazelScenarioDefinition
import org.gradle.profiler.buck.BuckScenarioDefinition
import org.gradle.profiler.buildops.BuildOperationMeasurement
import org.gradle.profiler.buildops.BuildOperationMeasurementKind
import org.gradle.profiler.gradle.GradleBuildInvoker
import org.gradle.profiler.gradle.GradleClientSpec
import org.gradle.profiler.gradle.GradleDaemonReuse
import org.gradle.profiler.gradle.GradleScenarioDefinition
import org.gradle.profiler.gradle.LoadToolingModelAction
import org.gradle.profiler.gradle.RunToolingAction
import org.gradle.profiler.maven.MavenScenarioDefinition
import org.gradle.profiler.mutations.AbstractScheduledMutator
import org.gradle.profiler.report.Format
import org.gradle.profiler.studio.AndroidStudioSyncAction
import org.gradle.profiler.studio.invoker.StudioGradleScenarioDefinition
import org.gradle.profiler.toolingapi.FetchProjectPublications
import org.gradle.tooling.model.idea.IdeaProject
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import static org.gradle.profiler.ScenarioLoader.loadScenarios
import static org.gradle.profiler.mutations.AbstractScheduledMutator.Schedule.BUILD
import static org.gradle.profiler.mutations.AbstractScheduledMutator.Schedule.CLEANUP
import static org.gradle.profiler.mutations.AbstractScheduledMutator.Schedule.SCENARIO

class ScenarioLoaderTest extends Specification {
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
            .setStudioInstallDir(tmpDir.newFolder())
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

    def "can load single scenario"() {
        def settings = settings()

        scenarioFile << """
            default {
                tasks = ["help"]
            }
        """
        def scenarios = loadScenarios(scenarioFile, settings, Mock(GradleBuildConfigurationReader))
        expect:
        scenarios*.name == ["default"]
        def scenario = scenarios[0] as GradleScenarioDefinition
        scenario.action.tasks == ["help"]
        scenario.cleanupAction == BuildAction.NO_OP
    }

    def "can load single scenario with no tasks defined"() {
        def settings = settings()
        settings.targets.add("default") // don't use the target as the default tasks

        scenarioFile << """
            default {
            }
        """
        def scenarios = loadScenarios(scenarioFile, settings, Mock(GradleBuildConfigurationReader))
        expect:
        def scenario = scenarios[0] as GradleScenarioDefinition
        scenario.action.tasks.empty
    }

    def "can load single scenarios with and without title"() {
        def settings = settings()
        settings.targets.addAll("entitled", "untitled") // don't use the target as the default tasks

        scenarioFile << """
            entitled {
                title = "This is a scenario with a title"
            }

            untitled {
            }
        """
        def scenarios = loadScenarios(scenarioFile, settings, Mock(GradleBuildConfigurationReader))
        expect:
        def entitled = scenarios[0] as GradleScenarioDefinition
        def untitled = scenarios[1] as GradleScenarioDefinition
        entitled.name == "entitled"
        entitled.title == "This is a scenario with a title"
        untitled.name == "untitled"
        untitled.title == "untitled"
    }

    def "scenario uses invoker specified on command-line when none is specified"() {
        scenarioFile << """
            default {
            }
            withInvoker {
                run-using = tooling-api
            }
        """
        def settings1 = settings(GradleBuildInvoker.ToolingApi)
        def settings2 = settings(GradleBuildInvoker.Cli)

        expect:
        def scenarios1 = loadScenarios(scenarioFile, settings1, Mock(GradleBuildConfigurationReader))
        (scenarios1[0] as GradleScenarioDefinition).invoker == GradleBuildInvoker.ToolingApi
        (scenarios1[1] as GradleScenarioDefinition).invoker == GradleBuildInvoker.ToolingApi

        def scenarios2 = loadScenarios(scenarioFile, settings2, Mock(GradleBuildConfigurationReader))
        (scenarios2[0] as GradleScenarioDefinition).invoker == GradleBuildInvoker.Cli
        (scenarios2[1] as GradleScenarioDefinition).invoker == GradleBuildInvoker.ToolingApi
    }

    def "scenario can define how to invoke Gradle"() {
        def settings = settings()
        scenarioFile << """
            cli {
                run-using = cli
            }
            toolingApi {
                run-using = tooling-api
            }
        """
        def scenarios = loadScenarios(scenarioFile, settings, Mock(GradleBuildConfigurationReader))

        expect:
        def cli = scenarios[0] as GradleScenarioDefinition
        cli.invoker == GradleBuildInvoker.Cli
        def toolingApi = scenarios[1] as GradleScenarioDefinition
        toolingApi.invoker == GradleBuildInvoker.ToolingApi
    }

    def "scenario can define system properties"() {
        def settings = settings()
        scenarioFile << """
            instantExecution {
                system-properties {
                    "org.gradle.unsafe.instant-execution" = true
                    "org.gradle.unsafe.instant-execution.fail-on-problems" = false
                }
            }
        """
        def scenarios = loadScenarios(scenarioFile, settings, Mock(GradleBuildConfigurationReader))

        expect:
        def instantExecution = scenarios[0] as GradleScenarioDefinition
        instantExecution.systemProperties == [
            "org.gradle.unsafe.instant-execution"                 : "true",
            "org.gradle.unsafe.instant-execution.fail-on-problems": "false"
        ]
    }

    def "scenario can define what state the daemon should be in for each measured build"() {
        def settings = settings(GradleBuildInvoker.ToolingApi)
        scenarioFile << """
            cliCold {
                run-using = cli
                daemon = cold
            }
            none {
                daemon = none
            }
            cold {
                daemon = cold
            }
            warm {
                daemon = warm
            }
        """
        def scenarios = loadScenarios(scenarioFile, settings, Mock(GradleBuildConfigurationReader))

        expect:
        def cliCold = scenarios[0] as GradleScenarioDefinition
        cliCold.invoker.client == GradleClientSpec.GradleCli
        cliCold.invoker.daemonReuse == GradleDaemonReuse.ColdDaemonOnly
        def cold = scenarios[1] as GradleScenarioDefinition
        cold.invoker.client == GradleClientSpec.ToolingApi
        cold.invoker.daemonReuse == GradleDaemonReuse.ColdDaemonOnly
        def none = scenarios[2] as GradleScenarioDefinition
        none.invoker == GradleBuildInvoker.CliNoDaemon
        def warm = scenarios[3] as GradleScenarioDefinition
        warm.invoker == GradleBuildInvoker.ToolingApi
    }

    def "uses warm-up and iteration counts based on command-line options when Gradle invocation defined by scenario"() {
        def benchmarkSettings = settingsBuilder(GradleBuildInvoker.ToolingApi, true).setWarmupCount(123).setIterations(509).build()
        def profileSettings = settingsBuilder(GradleBuildInvoker.ToolingApi, false).setWarmupCount(25).setIterations(44).build()

        scenarioFile << """
            default {
                run-using = tooling-api
                daemon = warm
                warm-ups = 5
                iterations = 2
            }
        """
        def benchmarkScenarios = loadScenarios(scenarioFile, benchmarkSettings, Mock(GradleBuildConfigurationReader))
        def profileScenarios = loadScenarios(scenarioFile, profileSettings, Mock(GradleBuildConfigurationReader))

        expect:
        def benchmarkScenario = benchmarkScenarios[0] as GradleScenarioDefinition
        benchmarkScenario.warmUpCount == 123
        benchmarkScenario.buildCount == 509

        def profileScenario = profileScenarios[0] as GradleScenarioDefinition
        profileScenario.warmUpCount == 25
        profileScenario.buildCount == 44
    }

    def "uses warm-up and iteration counts based on Gradle invocation defined by scenario"() {
        def benchmarkSettings = settings()
        def profileSettings = settings(GradleBuildInvoker.ToolingApi, false)

        scenarioFile << """
            default {
                run-using = ${runUsing}
                daemon = ${daemon}
            }
        """
        def benchmarkScenarios = loadScenarios(scenarioFile, benchmarkSettings, Mock(GradleBuildConfigurationReader))
        def profileScenarios = loadScenarios(scenarioFile, profileSettings, Mock(GradleBuildConfigurationReader))

        expect:
        def benchmarkScenario = benchmarkScenarios[0] as GradleScenarioDefinition
        benchmarkScenario.warmUpCount == warmups
        benchmarkScenario.buildCount == 10

        def profileScenario = profileScenarios[0] as GradleScenarioDefinition
        profileScenario.warmUpCount == profileWarmups
        profileScenario.buildCount == 1

        where:
        runUsing      | daemon | warmups | profileWarmups
        "tooling-api" | "warm" | 6       | 2
        "tooling-api" | "cold" | 1       | 1
        "cli"         | "warm" | 6       | 2
        "cli"         | "cold" | 1       | 1
        "cli"         | "none" | 1       | 1
    }

    def "uses warm-up and iteration counts defined by scenario"() {
        def benchmarkSettings = settings()
        def profileSettings = settings(GradleBuildInvoker.ToolingApi, false)

        scenarioFile << """
            default {
                run-using = ${runUsing}
                daemon = ${daemon}
                warm-ups = 5
                iterations = 2
            }
        """
        def benchmarkScenarios = loadScenarios(scenarioFile, benchmarkSettings, Mock(GradleBuildConfigurationReader))
        def profileScenarios = loadScenarios(scenarioFile, profileSettings, Mock(GradleBuildConfigurationReader))

        expect:
        def benchmarkScenario = benchmarkScenarios[0] as GradleScenarioDefinition
        benchmarkScenario.warmUpCount == 5
        benchmarkScenario.buildCount == 2

        def profileScenario = profileScenarios[0] as GradleScenarioDefinition
        profileScenario.warmUpCount == 5
        profileScenario.buildCount == 2

        where:
        runUsing      | daemon
        "tooling-api" | "warm"
        "tooling-api" | "cold"
        "cli"         | "warm"
        "cli"         | "cold"
        "cli"         | "none"
    }

    def "can load build operations to benchmark"() {
        def benchmarkSettings = settingsBuilder(GradleBuildInvoker.ToolingApi)
            .setBuildOperationMeasurements([new BuildOperationMeasurement("BuildOpCmdLine", BuildOperationMeasurementKind.DURATION_SUM)])
            .build()

        scenarioFile << """
            default {
                measured-build-ops = ["BuildOp1", "BuildOp2"]
            }
        """

        def benchmarkScenarios = loadScenarios(scenarioFile, benchmarkSettings, Mock(GradleBuildConfigurationReader))

        expect:
        def benchmarkScenario = benchmarkScenarios[0] as GradleScenarioDefinition
        benchmarkScenario.buildOperationMeasurements == [
            new BuildOperationMeasurement("BuildOpCmdLine", BuildOperationMeasurementKind.DURATION_SUM),
            new BuildOperationMeasurement("BuildOp1", BuildOperationMeasurementKind.DURATION_SUM),
            new BuildOperationMeasurement("BuildOp2", BuildOperationMeasurementKind.DURATION_SUM)
        ]
    }

    def "can load scenarios that fetch tooling models"() {
        def settings = settings()

        scenarioFile << """
            one {
                tooling-api {
                    model = "${IdeaProject.class.name}"
                }
            }
            two {
                tooling-api {
                    model = "${IdeaProject.class.name}"
                }
                tasks = ["help"]
            }
        """
        def scenarios = loadScenarios(scenarioFile, settings, Mock(GradleBuildConfigurationReader))
        expect:
        scenarios*.name == ["one", "two"]
        def scenario1 = scenarios[0] as GradleScenarioDefinition
        scenario1.action instanceof LoadToolingModelAction
        scenario1.action.toolingModel == IdeaProject
        scenario1.action.tasks == []
        def scenario2 = scenarios[1] as GradleScenarioDefinition
        scenario2.action instanceof LoadToolingModelAction
        scenario2.action.toolingModel == IdeaProject
        scenario2.action.tasks == ["help"]
    }

    def "can load scenarios that run tooling actions"() {
        def settings = settings()

        scenarioFile << """
            one {
                tooling-api {
                    action = "${FetchProjectPublications.class.name}"
                }
            }
            two {
                tooling-api {
                    action = "${FetchProjectPublications.class.name}"
                }
                tasks = ["help"]
            }
        """
        def scenarios = loadScenarios(scenarioFile, settings, Mock(GradleBuildConfigurationReader))
        expect:
        scenarios*.name == ["one", "two"]
        def scenario1 = scenarios[0] as GradleScenarioDefinition
        scenario1.action instanceof RunToolingAction
        scenario1.action.action instanceof FetchProjectPublications
        scenario1.action.tasks == []
        def scenario2 = scenarios[1] as GradleScenarioDefinition
        scenario2.action instanceof RunToolingAction
        scenario2.action.action instanceof FetchProjectPublications
        scenario2.action.tasks == ["help"]
    }

    def "can load default Android studio sync scenario"() {
        def settings = settings()
        def configurationReader = Mock(GradleBuildConfigurationReader)
        configurationReader.readConfiguration() >> new GradleBuildConfiguration(null, null, null, null, false, false)

        scenarioFile << """
            default {
                android-studio-sync { }
            }
        """
        def scenarios = loadScenarios(scenarioFile, settings, configurationReader)
        expect:
        scenarios*.name == ["default"]
        def scenarioDefinition = scenarios[0] as StudioGradleScenarioDefinition
        scenarioDefinition.action instanceof AndroidStudioSyncAction
    }

    def "loads default scenarios only"() {
        def settings = settings()

        scenarioFile << """
            default-scenarios = ["alma", "bela"]

            default {
                tasks = ["help"]
            }

            alma {
                tasks = ["alma"]
            }

            bela {
                tasks = ["bela"]
            }
        """
        def scenarios = loadScenarios(scenarioFile, settings, Mock(GradleBuildConfigurationReader))
        expect:
        scenarios*.name == ["alma", "bela"]
        (scenarios[0] as GradleScenarioDefinition).action.tasks == ["alma"]
        (scenarios[1] as GradleScenarioDefinition).action.tasks == ["bela"]
    }

    def "fails when default scenario does not exist"() {
        def settings = settings()

        scenarioFile << """
            default-scenarios = ["alma", "nonexistent"]

            default {
                tasks = ["help"]
            }

            alma {
                tasks = ["alma"]
            }
        """

        when:
        loadScenarios(scenarioFile, settings, Mock(GradleBuildConfigurationReader))

        then:
        def ex = thrown IllegalArgumentException
        ex.message == "Unknown scenario 'nonexistent' in default scenarios. Available scenarios are: alma, default"
    }

    def "loads included config"() {
        def settings = settings()

        def otherConf = tmpDir.newFile("other.conf")
        otherConf << """
            default-scenarios = ["alma"]
            alma {
                tasks = ["alma"]
            }
        """

        scenarioFile << """
            bela {
                tasks = ["bela"]
            }

            include file("${otherConf.absolutePath.replace((char) '\\', (char) '/')}")
        """
        def scenarios = loadScenarios(scenarioFile, settings, Mock(GradleBuildConfigurationReader))
        expect:
        scenarios*.name == ["alma"]
        (scenarios[0] as GradleScenarioDefinition).action.tasks == ["alma"]
    }

    private static String mockToolHome(String tool) {
        if (OperatingSystem.isWindows()) {
            return "C:/my/$tool/home"
        } else {
            return "/my/$tool/home"
        }
    }

    def "can load Bazel scenario"(String home) {
        def settings = settings(BuildInvoker.Bazel)

        scenarioFile << """
            default {
                bazel {
                    targets = ["help"]
                    ${home == null ? "" : "home = \"$home\""}
                }
            }
        """
        def scenarios = loadScenarios(scenarioFile, settings, Mock(GradleBuildConfigurationReader))
        expect:
        scenarios*.name == ["default"]
        (scenarios[0] as BazelScenarioDefinition).targets == ["help"]
        (scenarios[0] as BazelScenarioDefinition).toolHome?.absolutePath?.replace((char) '\\', (char) '/') == home

        where:
        home << [mockToolHome("bazel"), null]
    }

    def "can load Buck scenario"(String home) {
        def settings = settings(BuildInvoker.Buck)

        scenarioFile << """
            default {
                buck {
                    targets = ["help"]
                    ${home == null ? "" : "home = \"$home\""}
                }
            }
        """
        def scenarios = loadScenarios(scenarioFile, settings, Mock(GradleBuildConfigurationReader))
        expect:
        scenarios*.name == ["default"]
        (scenarios[0] as BuckScenarioDefinition).targets == ["help"]
        (scenarios[0] as BuckScenarioDefinition).toolHome?.absolutePath?.replace((char) '\\', (char) '/') == home
        where:
        home << [mockToolHome("buck"), null]
    }

    def "can load Maven scenario"(String home) {
        def settings = settings(BuildInvoker.Maven)

        scenarioFile << """
            default {
                maven {
                    targets = ["help"]
                    ${home == null ? "" : "home = \"$home\""}
                }
            }
        """
        def scenarios = loadScenarios(scenarioFile, settings, Mock(GradleBuildConfigurationReader))
        expect:
        scenarios*.name == ["default"]
        (scenarios[0] as MavenScenarioDefinition).targets == ["help"]
        (scenarios[0] as MavenScenarioDefinition).toolHome?.absolutePath?.replace((char) '\\', (char) '/') == home
        where:
        home << [mockToolHome("maven"), null]
    }

    def "can load scenario with multiple files for a single mutation"() {
        def settings = settings()

        def fileForMutation1 = new File(projectDir, "fileForMutation1.java")
        def fileForMutation2 = new File(projectDir, "fileForMutation2.kt")

        fileForMutation1.createNewFile()
        fileForMutation2.createNewFile()

        scenarioFile << """
            default {
                tasks = ["help"]

                apply-abi-change-to = ["${fileForMutation1.name}", "${fileForMutation2.name}"]
            }
        """
        def scenarios = loadScenarios(scenarioFile, settings, Mock(GradleBuildConfigurationReader))

        expect:
        scenarios*.name == ["default"]
    }

    def "allows cleanup action scheduled for #schedule with cold daemon invoker"() {
        def settings = settings()

        scenarioFile << """
            default {
                tasks = ["help"]
                daemon = cold

                clear-transform-cache-before = $schedule
            }
        """

        when:
        loadScenarios(scenarioFile, settings, Mock(GradleBuildConfigurationReader))

        then:
        noExceptionThrown()

        where:
        schedule << AbstractScheduledMutator.Schedule.values()
    }

    def "allows cleanup action scheduled for SCENARIO with warm daemon invoker"() {
        def settings = settings()

        scenarioFile << """
            default {
                tasks = ["help"]

                clear-transform-cache-before = ${SCENARIO}
            }
        """

        expect:
        loadScenarios(scenarioFile, settings, Mock(GradleBuildConfigurationReader))
    }

    def "fails with cleanup action scheduled for #schedule with warm daemon invoker"() {
        def settings = settings()

        scenarioFile << """
            default {
                tasks = ["help"]

                clear-transform-cache-before = $schedule
            }
        """

        when:
        loadScenarios(scenarioFile, settings, Mock(GradleBuildConfigurationReader))

        then:
        def ex = thrown IllegalStateException
        ex.message == "Scenario 'default' is invalid: ClearArtifactTransformCacheMutator($schedule) is not allowed to be executed between builds with invoker `gradle` command"

        where:
        schedule << [BUILD, CLEANUP]
    }

    def "can load scenario with build operations trace = #trace"() {
        def settings = settings()

        scenarioFile << """
            default {
                tasks = ["help"]
                build-ops-trace = $trace
            }
        """
        def scenarios = loadScenarios(scenarioFile, settings, Mock(GradleBuildConfigurationReader))

        expect:
        scenarios*.name == ["default"]
        def scenario = scenarios[0] as GradleScenarioDefinition
        scenario.isBuildOperationsTrace() == trace
        scenario.getBuildOperationsTracePathPrefix().endsWith("default")

        where:
        trace << [true, false]
    }

    def "can load scenarios from a group"() {
        def settings = settingsBuilder().setScenarioGroup("smoke-tests").build()

        scenarioFile << """
            scenario-groups {
                smoke-tests = ["scenario1", "scenario2"]
            }

            scenario1 {
                tasks = ["help"]
            }
            scenario2 {
                tasks = ["build"]
            }
            scenario3 {
                tasks = ["clean"]
            }
        """
        def scenarios = loadScenarios(scenarioFile, settings, Mock(GradleBuildConfigurationReader))

        expect:
        scenarios*.name == ["scenario1", "scenario2"]
    }

    def "throws error when group does not exist"() {
        def settings = settingsBuilder().setScenarioGroup("nonexistent").build()

        scenarioFile << """
            scenario-groups {
                smoke-tests = ["scenario1"]
            }

            scenario1 {
                tasks = ["help"]
            }
        """

        when:
        loadScenarios(scenarioFile, settings, Mock(GradleBuildConfigurationReader))

        then:
        def ex = thrown IllegalArgumentException
        ex.message == "Unknown scenario group 'nonexistent' requested. Available groups are: smoke-tests"
    }

    def "throws error when scenario-groups is missing"() {
        def settings = settingsBuilder().setScenarioGroup("smoke-tests").build()

        scenarioFile << """
            scenario1 {
                tasks = ["help"]
            }
        """

        when:
        loadScenarios(scenarioFile, settings, Mock(GradleBuildConfigurationReader))

        then:
        def ex = thrown IllegalArgumentException
        ex.message.startsWith("Unknown scenario group 'smoke-tests' requested. No 'scenario-groups' defined in scenario file")
    }

    def "throws error when mixing group with individual scenario names"() {
        def settings = settingsBuilder().setScenarioGroup("smoke-tests").setTargets(["scenario1"]).build()

        scenarioFile << """
            scenario-groups {
                smoke-tests = ["scenario1"]
            }

            scenario1 {
                tasks = ["help"]
            }
        """

        when:
        loadScenarios(scenarioFile, settings, Mock(GradleBuildConfigurationReader))

        then:
        def ex = thrown IllegalArgumentException
        ex.message == "Cannot specify both --group and individual scenario names. Use either only '--group smoke-tests' OR specify scenario names directly."
    }

    def "throws error when scenario in group does not exist"() {
        def settings = settingsBuilder().setScenarioGroup("smoke-tests").build()

        scenarioFile << """
            scenario-groups {
                smoke-tests = ["scenario1", "nonexistent"]
            }

            scenario1 {
                tasks = ["help"]
            }
            scenario2 {
                tasks = ["build"]
            }
        """

        when:
        loadScenarios(scenarioFile, settings, Mock(GradleBuildConfigurationReader))

        then:
        def ex = thrown IllegalArgumentException
        ex.message == "Unknown scenario 'nonexistent' in group 'smoke-tests'. Available scenarios are: scenario1, scenario2"
    }

    def "scenario-groups and default-scenarios are not treated as scenarios"() {
        def settings = settings()

        scenarioFile << """
            default-scenarios = ["scenario1"]

            scenario-groups {
                smoke-tests = ["scenario1"]
            }

            scenario1 {
                tasks = ["help"]
            }
        """
        def scenarios = loadScenarios(scenarioFile, settings, Mock(GradleBuildConfigurationReader))

        expect:
        scenarios*.name == ["scenario1"]
    }
}
