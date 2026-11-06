package org.gradle.profiler

import org.gradle.api.JavaVersion
import org.gradle.profiler.fixtures.AbstractProfilerIntegrationTest
import org.gradle.profiler.fixtures.compatibility.ide.IdeGradleJvmCompatibility
import org.gradle.profiler.instrument.GradleInstrumentation
import org.gradle.profiler.spock.extensions.ShowIdeLogsOnFailure
import org.gradle.profiler.ide.IdeType
import org.gradle.profiler.ide.launcher.IdeLauncher
import org.gradle.profiler.ide.launcher.IdeLauncherProvider
import org.gradle.profiler.ide.tools.IdePluginInstaller
import org.gradle.profiler.ide.tools.IdeSandboxCreator
import spock.lang.Timeout

import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@ShowIdeLogsOnFailure
@Timeout(value = 5, unit = TimeUnit.MINUTES)
abstract class AbstractIdeSyncIntegrationTest extends AbstractProfilerIntegrationTest {

    private static final File REAL_M2_DIR = new File(System.getProperty("user.home"), ".m2")
    private static final File REAL_M2_REPOSITORY_DIR = new File(REAL_M2_DIR, "repository")

    File sandboxDir
    /**
     * Isolated user.home for the IDE process, so the IDE doesn't write to the real home directory of the machine.
     * E.g. IntelliJ's bundled Maven plugin creates ~/.m2/repository when opening any project,
     * which breaks other builds on shared CI agents that check that ~/.m2 isn't written.
     */
    File ideUserHome
    File ideHome
    String scenarioName
    String ideDisplayName
    String scenarioSyncOption
    String installDirOption
    String sandboxDirOption
    private boolean realM2DirExisted
    private boolean realM2RepositoryDirExisted

    abstract File findIdeHome()

    abstract IdeType ideType()

    def setup() {
        realM2DirExisted = REAL_M2_DIR.exists()
        realM2RepositoryDirExisted = REAL_M2_REPOSITORY_DIR.exists()

        sandboxDir = tmpDir.createDir('sandbox')
        ideUserHome = tmpDir.createDir('ide-user-home')
        ideHome = findIdeHome()
        ideDisplayName = ideType().displayName
        scenarioSyncOption = ideType() == IdeType.INTELLIJ_IDEA ? "idea-sync" : "studio-sync"
        installDirOption = ideType() == IdeType.INTELLIJ_IDEA ? "--idea-install-dir" : "--studio-install-dir"
        sandboxDirOption = ideType() == IdeType.INTELLIJ_IDEA ? "--idea-sandbox-dir" : "--studio-sandbox-dir"

        // IDE must support Java version we're going to run the test build with.
        // So far we expect the current version to work always, however some downgrading logic
        // may need to be applied in future.
        Set<Integer> supportedJavaVersionsByIde = IdeGradleJvmCompatibility.fromIdeHome(ideType(), ideHome).supportedJavaVersions
        Integer currentJvm = JavaVersion.current().majorVersion as int
        assert supportedJavaVersionsByIde.contains(currentJvm)

        // Explicit way to set JVM version to run Gradle with.
        // See: https://www.jetbrains.com/help/idea/gradle-jvm-selection.html#jdk_existing_project
        new File(projectDir, "gradle.properties") << "\norg.gradle.java.home=${System.getProperty("java.home").replace('\\', '/')}"

        setupProject()
        scenarioName = "scenario"
    }

    def cleanup() {
        stopIdeGradleDaemons()
        assertRealM2NotCreated()
    }

    void setupProject() {}

    /**
     * Measured sync invocations get the profiler's --gradle-user-home, and the profiler stops those daemons.
     * Tooling API calls the IDE makes on its own use <ideUserHome>/.gradle instead. IntelliJ stops that daemon when it shuts down normally,
     * but if the IDE is killed (e.g. on a test timeout) the daemon would keep running and, on Windows, keep files in the test directory open.
     */
    private void stopIdeGradleDaemons() {
        if (ideUserHome == null) {
            return
        }
        def daemonLogs = new File(ideUserHome, ".gradle/daemon").listFiles()?.collectMany { it.listFiles()?.toList() ?: [] } ?: []
        daemonLogs.collect { (it.name =~ /^daemon-(\d+)\.out\.log$/) }
            .findAll { it.matches() }
            .collect { it.group(1) as long }
            .each { pid ->
                ProcessHandle.of(pid).ifPresent { process ->
                    println("Stopping Gradle daemon $pid started by the IDE")
                    process.destroy()
                    try {
                        process.onExit().get(30, TimeUnit.SECONDS)
                    } catch (TimeoutException ignored) {
                        process.destroyForcibly()
                    }
                }
            }
    }

    /**
     * Guards against the IDE writing to the real ~/.m2, which fails other builds on shared CI agents.
     * Deletes what was created so the agent stays clean, then fails the test.
     */
    private void assertRealM2NotCreated() {
        def created = []
        if (!realM2DirExisted && REAL_M2_DIR.exists()) {
            created << REAL_M2_DIR
            REAL_M2_DIR.deleteDir()
        } else if (!realM2RepositoryDirExisted && REAL_M2_REPOSITORY_DIR.exists()) {
            created << REAL_M2_REPOSITORY_DIR
            REAL_M2_REPOSITORY_DIR.deleteDir()
        }
        assert created.isEmpty(): "Test created ${created} in the real home directory"
    }

    String ideUserHomeJvmArg() {
        // Forward slashes, since backslashes are escape characters in the scenario file
        return "-Duser.home=${ideUserHome.absolutePath.replace('\\', '/')}"
    }

    /**
     * The IDE sync block of a scenario. Always runs the IDE with an isolated user.home.
     */
    String ideSyncBlock(List<String> ideJvmArgs = ScenarioLoader.DEFAULT_IDE_JVM_ARGS, List<String> ideaProperties = []) {
        def jvmArgs = (ideJvmArgs + ideUserHomeJvmArg()).collect { "\"$it\"" }.join(", ")
        def properties = ideaProperties.isEmpty()
            ? ""
            : "idea-properties = [${ideaProperties.collect { "\"$it\"" }.join(", ")}]"
        return """$scenarioSyncOption {
                    ide-jvm-args = [$jvmArgs]
                    $properties
                }"""
    }

    def "benchmarks IDE sync with latest gradle version"() {
        given:
        def scenarioFile = file("performance.scenarios") << """
            $scenarioName {
                ${ideSyncBlock()}
            }
        """

        when:
        runBenchmark(scenarioFile, 2, 3)

        then:
        logFile.find("Gradle invocation 1 has completed in").size() == 5
        logFile.find("Full sync has completed in").size() == 5
        logFile.find("and it SUCCEEDED").size() == 5
        logFile.find("* Cleaning $ideDisplayName cache, this will require a restart...").size() == 0
        logFile.find("* Starting $ideDisplayName").size() == 1

        and:
        resultFile.lines[3] == "value,total execution time,Gradle total execution time,IDE execution time"
    }

    def "benchmarks IDE sync for project with buildSrc"() {
        // This tests that the IDE can call Gradle multiple times during a sync
        given:
        new File(projectDir, "buildSrc").mkdirs()
        new File(projectDir, "buildSrc/gradle.build").createNewFile()
        def scenarioFile = file("performance.scenarios") << """
            $scenarioName {
                ${ideSyncBlock()}
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
        logFile.find("* Cleaning $ideDisplayName cache, this will require a restart...").size() == 0
        logFile.find("* Starting $ideDisplayName").size() == 1

        and:
        resultFile.lines[3] == "value,total execution time,Gradle total execution time,IDE execution time"
    }

    def "benchmarks IDE sync by cleaning ide cache before build"() {
        given:
        def scenarioFile = file("performance.scenarios") << """
            $scenarioName {
                ${ideSyncBlock()}
                clear-android-studio-cache-before = BUILD
            }
        """

        when:
        runBenchmark(scenarioFile, 1, 2)

        then:
        logFile.find("Gradle invocation 1 has completed in").size() == 3
        logFile.find("Full sync has completed in").size() == 3
        logFile.find("and it SUCCEEDED").size() == 3
        logFile.find("* Cleaning $ideDisplayName cache, this will require a restart...").size() == 3
        // 4 since on first run we start IDE, clean cache and restart
        logFile.find("* Starting $ideDisplayName").size() == 4
    }

    def "benchmarks IDE sync by cleaning ide cache before scenario"() {
        given:
        def scenarioFile = file("performance.scenarios") << """
            $scenarioName {
                ${ideSyncBlock()}
                clear-android-studio-cache-before = SCENARIO
            }
        """

        when:
        runBenchmark(scenarioFile, 1, 2)

        then:
        logFile.find("Gradle invocation 1 has completed in").size() == 3
        logFile.find("Full sync has completed in").size() == 3
        logFile.find("and it SUCCEEDED").size() == 3
        logFile.find("* Cleaning $ideDisplayName cache, this will require a restart...").size() == 1
        // 2 since on first run we start IDE, clean cache and restart
        logFile.find("* Starting $ideDisplayName").size() == 2
    }

    def "detects if two IDE processes are running in the same sandbox"() {
        given:
        File otherProjectDir = tmpDir.createDir('project2')
        // We have to install plugin so also the first process is run in the headless mode.
        // We install plugin directory to a different "plugins-2" directory for first process otherwise cleaning plugin directory at start of second process fails on Windows.
        IdeSandboxCreator.IdeSandbox sandbox = IdeSandboxCreator.createSandbox(sandboxDir.toPath(), "plugins-2")
        IdeLauncher launcher = new IdeLauncherProvider(ideType(), ideHome.toPath(), sandbox, [ideUserHomeJvmArg()], []).get()
        IdePluginInstaller pluginInstaller = new IdePluginInstaller(sandbox.getPluginsDir())
        // We have to install plugin, since a plugin contains headless starter and it makes it run headless on CI
        pluginInstaller.installPlugin(Collections.singletonList(GradleInstrumentation.unpackPlugin("ide-plugin").toPath()))
        def scenarioFile = file("performance.scenarios") << """
            $scenarioName {
                ${ideSyncBlock()}
            }
        """

        when:
        CommandExec.RunHandle process = launcher.launchIde(otherProjectDir)
        runBenchmark(scenarioFile, 1, 1)

        then:
        def e = thrown(Main.ScenarioFailedException)
        e.getCause().message == "Timeout waiting for incoming connection from start-detector."
        logFile.containsOne("* ERROR")
        logFile.containsOne("* Could not connect to $ideDisplayName process started by the gradle-profiler.")
        logFile.containsOne("* This might indicate that you are already running a $ideDisplayName process in the same sandbox.")
        logFile.containsOne("* Stop $ideDisplayName manually in the used sandbox or use a different sandbox to isolate the process.")

        cleanup:
        process.kill()
    }

    def "allows two IDE processes in different sandboxes"() {
        given:
        File sandboxDir1 = tmpDir.createDir('sandbox1')
        // We create a different folder for project for the other process,
        // since if the IDE writes to same project at the same time, it can fail
        File otherProjectDir = tmpDir.createDir('project2')
        IdeSandboxCreator.IdeSandbox sandbox = IdeSandboxCreator.createSandbox(sandboxDir1.toPath())
        IdeLauncher launcher = new IdeLauncherProvider(ideType(), ideHome.toPath(), sandbox, [ideUserHomeJvmArg()], []).get()
        // We have to install plugin, since a plugin contains headless starter and it makes it run headless on CI
        IdePluginInstaller pluginInstaller = new IdePluginInstaller(sandbox.getPluginsDir())
        pluginInstaller.installPlugin(Collections.singletonList(GradleInstrumentation.unpackPlugin("ide-plugin").toPath()))
        def scenarioFile = file("performance.scenarios") << """
            $scenarioName {
                ${ideSyncBlock()}
            }
        """

        when:
        CommandExec.RunHandle process = launcher.launchIde(otherProjectDir)
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
                ${ideSyncBlock()}
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
                ${ideSyncBlock()}
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
                ${ideSyncBlock(["-Xmx4104m", "-Xms128m"])}
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

    def "doesn't write external annotations to .m2 folder"() {
        given:
        def scenarioFile = file("performance.scenarios") << """
            $scenarioName {
                ${ideSyncBlock()}
            }
        """
        buildFile.text = """
            plugins {
                id 'java'
            }
            repositories {
                mavenCentral()
            }
            dependencies {
                // This dependency triggers download of external annotations
                // to <mavenRepo>/org/jetbrains/externalAnnotations
                implementation("com.fasterxml.jackson.core:jackson-core:2.9.6")
            }
        """
        // The IDE resolves both its local Maven repository and the MAVEN_REPOSITORY path macro
        // (where external annotations would be downloaded to) from user.home.
        def ideMavenRepository = new File(ideUserHome, ".m2/repository")

        when:
        runBenchmark(scenarioFile, 1, 1)

        then:
        // IntelliJ's Maven plugin creates an empty local repository when indexing it, but nothing must be downloaded into it
        !ideMavenRepository.exists() || ideMavenRepository.list() == [] as String[]
    }

    def "can add idea.properties"() {
        given:
        // Use a harmless cosmetic key that is registered in IntelliJ's Registry (defined in registry.properties)
        def registryKey = "ide.balloon.shadowEnabled"
        def scenarioFile = file("performance.scenarios") << """
            $scenarioName {
                ${ideSyncBlock(ScenarioLoader.DEFAULT_IDE_JVM_ARGS, ["${registryKey}=true"])}
            }
        """

        when:
        runBenchmark(scenarioFile, 1, 1)

        then:
        logFile.find("Full sync has completed in").size() == 2
        logFile.find("and it SUCCEEDED").size() == 2
        def ideaPropertiesFile = new File(sandboxDir, "scenarioOptions/idea.properties")
        def ideaProperties = ideaPropertiesFile.readLines()
        ideaProperties.contains(registryKey + "=true")

        and:
        def ideaLog = new File(sandboxDir, "logs/idea.log")
        ideaLog.exists()
        ideaLog.text.contains("Modified registry entries: [" + registryKey + "=true]")
    }

    def runBenchmark(File scenarioFile, int warmups, int iterations, String... additionalArgs) {
        List<String> args = [
            "--project-dir", projectDir.absolutePath,
            "--output-dir", outputDir.absolutePath,
            "--gradle-version", latestSupportedGradleVersion,
            "--benchmark",
            "--scenario-file", scenarioFile.getAbsolutePath(),
            installDirOption, ideHome.absolutePath,
            sandboxDirOption, sandboxDir.absolutePath,
            "--warmups", "$warmups",
            "--iterations", "$iterations",
            *additionalArgs,
            scenarioName
        ]
        new Main().run(*args)
    }
}
