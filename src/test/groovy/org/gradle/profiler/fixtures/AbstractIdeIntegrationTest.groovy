package org.gradle.profiler.fixtures

import org.gradle.profiler.CommandExec
import org.gradle.profiler.Main
import org.gradle.profiler.instrument.GradleInstrumentation
import org.gradle.profiler.spock.extensions.ShowIdeLogsOnFailure
import org.gradle.profiler.ide.launcher.IdeLauncher
import org.gradle.profiler.ide.launcher.IdeLauncherProvider
import org.gradle.profiler.ide.tools.IdePluginInstaller
import org.gradle.profiler.ide.tools.IdeSandboxCreator

/**
 * Shared integration tests for IDE sync benchmarking.
 * Subclasses must set {@link #ideHome} in their {@code setup()} method.
 */
@ShowIdeLogsOnFailure
abstract class AbstractIdeIntegrationTest extends AbstractProfilerIntegrationTest {

    File sandboxDir
    /** Set by each subclass's {@code setup()} to point at the IDE installation under test. */
    File ideHome
    String scenarioName

    def setup() {
        sandboxDir = tmpDir.createDir('sandbox')
        scenarioName = "scenario"
    }

    def "benchmarks IDE sync with latest gradle version"() {
        given:
        def scenarioFile = file("performance.scenarios") << """
            $scenarioName {
                ide-sync {
                }
            }
        """

        when:
        runBenchmark(scenarioFile, 2, 3)

        then:
        logFile.find("Gradle invocation 1 has completed in").size() == 5
        logFile.find("Full sync has completed in").size() == 5
        logFile.find("and it SUCCEEDED").size() == 5
        logFile.find("* Cleaning IDE cache, this will require a restart...").size() == 0
        logFile.find("* Starting IDE at").size() == 1

        and:
        resultFile.lines[3] == "value,total execution time,Gradle total execution time,IDE execution time"
    }

    def "benchmarks IDE sync for project with buildSrc"() {
        // Tests that the IDE can call Gradle multiple times during a sync (once for buildSrc, once for root)
        given:
        new File(projectDir, "buildSrc").mkdirs()
        new File(projectDir, "buildSrc/gradle.build").createNewFile()
        def scenarioFile = file("performance.scenarios") << """
            $scenarioName {
                ide-sync {
                }
            }
        """

        when:
        runBenchmark(scenarioFile, 1, 1)

        then:
        logFile.find("Gradle invocation 1 has completed in").size() == 2
        def duration = (logFile.text =~ /Gradle invocation 1 has completed in: (\d+)ms/)
            .findAll()
            .collect { it[1] as Integer }
        logFile.find("Full Gradle execution time: ${duration[0]}ms").size() == 1
        logFile.find("Full sync has completed in").size() == 2
        logFile.find("and it SUCCEEDED").size() == 2
        logFile.find("* Cleaning IDE cache, this will require a restart...").size() == 0
        logFile.find("* Starting IDE at").size() == 1

        and:
        resultFile.lines[3] == "value,total execution time,Gradle total execution time,IDE execution time"
    }

    def "benchmarks IDE sync by cleaning ide cache before build"() {
        given:
        def scenarioFile = file("performance.scenarios") << """
            $scenarioName {
                ide-sync {}
                clear-ide-cache-before = BUILD
            }
        """

        when:
        runBenchmark(scenarioFile, 1, 2)

        then:
        logFile.find("Gradle invocation 1 has completed in").size() == 3
        logFile.find("Full sync has completed in").size() == 3
        logFile.find("and it SUCCEEDED").size() == 3
        logFile.find("* Cleaning IDE cache, this will require a restart...").size() == 3
        // 4 since on first run we start IDE, clean cache and restart
        logFile.find("* Starting IDE at").size() == 4
    }

    def "benchmarks IDE sync by cleaning ide cache before scenario"() {
        given:
        def scenarioFile = file("performance.scenarios") << """
            $scenarioName {
                ide-sync {}
                clear-ide-cache-before = SCENARIO
            }
        """

        when:
        runBenchmark(scenarioFile, 1, 2)

        then:
        logFile.find("Gradle invocation 1 has completed in").size() == 3
        logFile.find("Full sync has completed in").size() == 3
        logFile.find("and it SUCCEEDED").size() == 3
        logFile.find("* Cleaning IDE cache, this will require a restart...").size() == 1
        // 2 since on first run we start IDE, clean cache and restart
        logFile.find("* Starting IDE at").size() == 2
    }

    def "detects if two IDE processes are running in the same sandbox"() {
        given:
        File otherIdeProjectDir = tmpDir.createDir('project2')
        // Install plugin to a different "plugins-2" directory for the first process otherwise
        // cleaning plugin directory at start of the second process fails on Windows.
        IdeSandboxCreator.IdeSandbox sandbox = IdeSandboxCreator.createSandbox(sandboxDir.toPath(), "plugins-2")
        IdeLauncher launcher = new IdeLauncherProvider(ideHome.toPath(), sandbox, [], []).get()
        IdePluginInstaller pluginInstaller = new IdePluginInstaller(sandbox.getPluginsDir())
        // Plugin contains the headless starter which makes the IDE run headless on CI
        pluginInstaller.installPlugin(Collections.singletonList(GradleInstrumentation.unpackPlugin("intellij-plugin").toPath()))
        def scenarioFile = file("performance.scenarios") << """
            $scenarioName {
                ide-sync {
                }
            }
        """

        when:
        CommandExec.RunHandle process = launcher.launch(otherIdeProjectDir)
        runBenchmark(scenarioFile, 1, 1)

        then:
        def e = thrown(Main.ScenarioFailedException)
        e.getCause().message == "Timeout waiting for incoming connection from start-detector."
        logFile.containsOne("* ERROR")
        logFile.containsOne("* Could not connect to the IDE process started by the gradle-profiler.")
        logFile.containsOne("* This might indicate that you are already running an IDE process in the same sandbox.")
        logFile.containsOne("* Stop the IDE manually in the used sandbox or use a different sandbox with --ide-sandbox-dir to isolate the process.")

        cleanup:
        process.kill()
    }

    def "allows two IDE processes in different sandboxes"() {
        given:
        File sandboxDir1 = tmpDir.createDir('sandbox1')
        // Use a different project dir for the other process since concurrent writes to the same
        // project directory can fail.
        File otherIdeProjectDir = tmpDir.createDir('project2')
        IdeSandboxCreator.IdeSandbox sandbox = IdeSandboxCreator.createSandbox(sandboxDir1.toPath())
        IdeLauncher launcher = new IdeLauncherProvider(ideHome.toPath(), sandbox, [], []).get()
        IdePluginInstaller pluginInstaller = new IdePluginInstaller(sandbox.getPluginsDir())
        pluginInstaller.installPlugin(Collections.singletonList(GradleInstrumentation.unpackPlugin("intellij-plugin").toPath()))
        def scenarioFile = file("performance.scenarios") << """
            $scenarioName {
                ide-sync {
                }
            }
        """

        when:
        CommandExec.RunHandle process = launcher.launch(otherIdeProjectDir)
        runBenchmark(scenarioFile, 1, 1)

        then:
        logFile.find("Gradle invocation 1 has completed in").size() == 2
        logFile.find("Full sync has completed in").size() == 2

        cleanup:
        process.kill()
    }

    def "fails fast if IDE sync fails"() {
        given:
        def scenarioFile = file("performance.scenarios") << """
            $scenarioName {
                ide-sync {
                }
            }
        """
        buildFile << """
            if (System.getProperty("idea.sync.active") != null) {
                throw new GradleException("Sync test failure")
            }
        """

        when:
        runBenchmark(scenarioFile, 1, 2)

        then:
        def e = thrown(Main.ScenarioFailedException)
        e.getCause().message.startsWith("Gradle sync has failed with error message: 'Sync test failure'.")
        logFile.find("Full Gradle execution time").size() == 1
        logFile.find("Full sync has completed in").size() == 1
        logFile.find("and it FAILED").size() == 1
    }

    def "benchmarks IDE sync with gc measurement, configuration time measurement and operation time measurement"() {
        given:
        def scenarioFile = file("performance.scenarios") << """
            $scenarioName {
                ide-sync {
                }
            }
        """
        buildFile << """
            System.gc()
        """

        when:
        runBenchmark(scenarioFile, 1, 2,
            "--measure-gc",
            "--measure-config-time",
            "--measure-build-op", "org.gradle.initialization.ConfigureBuildBuildOperationType")

        then:
        logFile.find("Gradle invocation 1 has completed in").size() == 3
        logFile.find("Full sync has completed in").size() == 3
        logFile.find("and it SUCCEEDED").size() == 3

        and:
        def lines = resultFile.lines
        lines[3] == "value,total execution time,garbage collection time,task start,ConfigureBuildBuildOperationType (Cumulative Time),Gradle total execution time,IDE execution time"
        def matcher = lines[4] =~ /warm-up build #1,($SAMPLE),(?<gc>$SAMPLE),(?<taskStart>$SAMPLE),(?<buildOp>$SAMPLE),($SAMPLE),($SAMPLE)/
        matcher.matches()
        assert matcher.group("gc") as double > 0
        assert matcher.group("taskStart") as double > 0
        assert matcher.group("buildOp") as double > 0
    }

    def "can override IDE jvm args"() {
        given:
        def scenarioFile = file("performance.scenarios") << """
            $scenarioName {
                ide-sync {
                    ide-jvm-args = ["-Xmx4104m", "-Xms128m"]
                }
            }
        """

        when:
        runBenchmark(scenarioFile, 1, 1)

        then:
        logFile.find("Full sync has completed in").size() == 2
        logFile.find("and it SUCCEEDED").size() == 2
        def vmOptionsFile = new File(sandboxDir, "scenarioOptions/idea.vmoptions")
        vmOptionsFile.exists()
        def vmOptions = vmOptionsFile.readLines()
        vmOptions.contains("-Xmx4104m")
        vmOptions.contains("-Xms128m")
    }

    def "can add idea.properties"() {
        given:
        def scenarioFile = file("performance.scenarios") << """
            $scenarioName {
                ide-sync {
                    idea-properties = ["foo=true"]
                }
            }
        """

        when:
        runBenchmark(scenarioFile, 1, 1)

        then:
        logFile.find("Full sync has completed in").size() == 2
        logFile.find("and it SUCCEEDED").size() == 2
        def ideaPropertiesFile = new File(sandboxDir, "scenarioOptions/idea.properties")
        def ideaProperties = ideaPropertiesFile.readLines()
        ideaProperties.contains("foo=true")
    }

    def runBenchmark(File scenarioFile, int warmups, int iterations, String... additionalArgs) {
        List<String> args = [
            "--project-dir", projectDir.absolutePath,
            "--output-dir", outputDir.absolutePath,
            "--gradle-version", latestSupportedGradleVersion,
            "--benchmark",
            "--scenario-file", scenarioFile.getAbsolutePath(),
            "--ide-install-dir", ideHome.absolutePath,
            "--ide-sandbox-dir", sandboxDir.absolutePath,
            "--warmups", "$warmups",
            "--iterations", "$iterations",
            *additionalArgs,
            scenarioName
        ]
        new Main().run(*args)
    }
}
