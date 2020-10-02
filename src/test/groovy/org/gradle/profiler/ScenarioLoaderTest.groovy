package org.gradle.profiler

import com.google.common.collect.ImmutableList
import org.gradle.profiler.mutations.AbstractCleanupMutator
import org.gradle.profiler.report.CsvGenerator
import org.gradle.tooling.model.idea.IdeaProject
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.profiler.ScenarioLoader.loadScenarios
import static org.gradle.profiler.mutations.AbstractCleanupMutator.CleanupSchedule.BUILD
import static org.gradle.profiler.mutations.AbstractCleanupMutator.CleanupSchedule.CLEANUP
import static org.gradle.profiler.mutations.AbstractCleanupMutator.CleanupSchedule.SCENARIO

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

    private settings(
        BuildInvoker invoker = GradleBuildInvoker.Cli,
        boolean benchmark = true,
        Integer warmups = null,
        Integer iterations = null,
        List<String> measuredBuildOperations = ImmutableList.of()
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
            .setWarmupCount(warmups)
            .setIterations(iterations)
            .setMeasureGarbageCollection(false)
            .setMeasureConfigTime(false)
            .setMeasuredBuildOperations(measuredBuildOperations)
            .setCsvFormat(CsvGenerator.Format.WIDE
            ).build()
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
        def benchmarkSettings = settings(GradleBuildInvoker.ToolingApi, true, 123, 509)
        def profileSettings = settings(GradleBuildInvoker.ToolingApi, false, 25, 44)

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
        def benchmarkSettings = settings(GradleBuildInvoker.ToolingApi, true, null, null, ["BuildOpCmdLine"])

        scenarioFile << """
            default {
                measured-build-ops = ["BuildOp1", "BuildOp2"]
            }
        """

        def benchmarkScenarios = loadScenarios(scenarioFile, benchmarkSettings, Mock(GradleBuildConfigurationReader))

        expect:
        def benchmarkScenario = benchmarkScenarios[0] as GradleScenarioDefinition
        benchmarkScenario.measuredBuildOperations == ["BuildOpCmdLine", "BuildOp1", "BuildOp2"]
    }

    def "can load tooling model scenarios"() {
        def settings = settings()

        scenarioFile << """
            one {
                model = "${IdeaProject.class.name}"
            }
            two {
                model = "${IdeaProject.class.name}"
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

    def "can load default Android studio sync scenario"() {
        def settings = settings()

        scenarioFile << """
            default {
                android-studio-sync { }
            }
        """
        def scenarios = loadScenarios(scenarioFile, settings, Mock(GradleBuildConfigurationReader))
        expect:
        scenarios*.name == ["default"]
        def scenarioDefinition = scenarios[0] as GradleScenarioDefinition
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

    @Unroll
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

    @Unroll
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

    @Unroll
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

    @Unroll
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
        schedule << AbstractCleanupMutator.CleanupSchedule.values()
    }

    @Unroll
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

    @Unroll
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
}
