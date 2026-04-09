package org.gradle.profiler

import org.gradle.api.JavaVersion
import org.gradle.profiler.fixtures.AbstractProfilerIntegrationTest
import org.gradle.profiler.fixtures.compatibility.ide.IntellijGradleJvmCompatibility
import org.gradle.profiler.instrument.GradleInstrumentation
import org.gradle.profiler.spock.extensions.ShowAndroidStudioLogsOnFailure
import org.gradle.profiler.studio.launcher.IdeLauncher
import org.gradle.profiler.studio.launcher.IdeLauncherProvider
import org.gradle.profiler.studio.tools.IdePluginInstaller
import org.gradle.profiler.studio.tools.IdeSandboxCreator
import spock.lang.Timeout

import java.util.concurrent.TimeUnit

@ShowAndroidStudioLogsOnFailure
@Timeout(value = 5, unit = TimeUnit.MINUTES)
abstract class AbstractIdeSyncIntegrationTest extends AbstractProfilerIntegrationTest {

    File sandboxDir
    File ideHome
    String scenarioName

    abstract File findIdeHome()

    def setup() {
        sandboxDir = tmpDir.createDir('sandbox')
        ideHome = findIdeHome()

        // IDE must support Java version we're going to run the test build with.
        // So far we expect the current version to work always, however some downgrading logic
        // may need to be applied in future.
        Set<Integer> supportedJavaVersionsByIde = IntellijGradleJvmCompatibility.fromIdeHome(ideHome).supportedJavaVersions
        Integer currentJvm = JavaVersion.current().majorVersion as int
        assert supportedJavaVersionsByIde.contains(currentJvm)

        // Explicit way to set JVM version to run Gradle with.
        // See: https://www.jetbrains.com/help/idea/gradle-jvm-selection.html#jdk_existing_project
        new File(projectDir, "gradle.properties") << "\norg.gradle.java.home=${System.getProperty("java.home").replace('\\', '/')}"

        setupProject()
        scenarioName = "scenario"
    }

    /**
     * Hook for subclasses to do additional project setup (e.g. write local.properties for Android SDK).
     */
    void setupProject() {}

    def "benchmarks IDE sync with latest gradle version"() {
        given:
        def scenarioFile = file("performance.scenarios") << """
            $scenarioName {
                android-studio-sync {
                }
            }
        """

        when:
        runBenchmark(scenarioFile, 2, 3)

        then:
        logFile.find("Gradle invocation 1 has completed in").size() == 5
        logFile.find("Full sync has completed in").size() == 5
        logFile.find("and it SUCCEEDED").size() == 5
        logFile.find("* Cleaning Android Studio cache, this will require a restart...").size() == 0
        logFile.find("* Starting Android Studio").size() == 1

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
                android-studio-sync {
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
        logFile.find("* Cleaning Android Studio cache, this will require a restart...").size() == 0
        logFile.find("* Starting Android Studio").size() == 1

        and:
        resultFile.lines[3] == "value,total execution time,Gradle total execution time,IDE execution time"
    }

    def "benchmarks IDE sync by cleaning ide cache before build"() {
        given:
        def scenarioFile = file("performance.scenarios") << """
            $scenarioName {
                android-studio-sync {}
                clear-android-studio-cache-before = BUILD
            }
        """

        when:
        runBenchmark(scenarioFile, 1, 2)

        then:
        logFile.find("Gradle invocation 1 has completed in").size() == 3
        logFile.find("Full sync has completed in").size() == 3
        logFile.find("and it SUCCEEDED").size() == 3
        logFile.find("* Cleaning Android Studio cache, this will require a restart...").size() == 3
        // 4 since on first run we start IDE, clean cache and restart
        logFile.find("* Starting Android Studio").size() == 4
    }

    def "benchmarks IDE sync by cleaning ide cache before scenario"() {
        given:
        def scenarioFile = file("performance.scenarios") << """
            $scenarioName {
                android-studio-sync {}
                clear-android-studio-cache-before = SCENARIO
            }
        """

        when:
        runBenchmark(scenarioFile, 1, 2)

        then:
        logFile.find("Gradle invocation 1 has completed in").size() == 3
        logFile.find("Full sync has completed in").size() == 3
        logFile.find("and it SUCCEEDED").size() == 3
        logFile.find("* Cleaning Android Studio cache, this will require a restart...").size() == 1
        // 2 since on first run we start IDE, clean cache and restart
        logFile.find("* Starting Android Studio").size() == 2
    }

    def "detects if two IDE processes are running in the same sandbox"() {
        given:
        File otherProjectDir = tmpDir.createDir('project2')
        // We have to install plugin so also the first process is run in the headless mode.
        // We install plugin directory to a different "plugins-2" directory for first process otherwise cleaning plugin directory at start of second process fails on Windows.
        IdeSandboxCreator.IdeSandbox sandbox = IdeSandboxCreator.createSandbox(sandboxDir.toPath(), "plugins-2")
        IdeLauncher launcher = new IdeLauncherProvider(ideHome.toPath(), sandbox, [], []).get()
        IdePluginInstaller pluginInstaller = new IdePluginInstaller(sandbox.getPluginsDir())
        // We have to install plugin, since a plugin contains headless starter and it makes it run headless on CI
        pluginInstaller.installPlugin(Collections.singletonList(GradleInstrumentation.unpackPlugin("studio-plugin").toPath()))
        def scenarioFile = file("performance.scenarios") << """
            $scenarioName {
                android-studio-sync {
                }
            }
        """

        when:
        CommandExec.RunHandle process = launcher.launchStudio(otherProjectDir)
        runBenchmark(scenarioFile, 1, 1)

        then:
        def e = thrown(Main.ScenarioFailedException)
        e.getCause().message == "Timeout waiting for incoming connection from start-detector."
        logFile.containsOne("* ERROR")
        logFile.containsOne("* Could not connect to IDE process started by the gradle-profiler.")
        logFile.containsOne("* This might indicate that you are already running an IDE process in the same sandbox.")
        logFile.containsOne("* Stop the IDE manually in the used sandbox or use a different sandbox with --ide-sandbox-dir to isolate the process.")

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
        IdeLauncher launcher = new IdeLauncherProvider(ideHome.toPath(), sandbox, [], []).get()
        // We have to install plugin, since a plugin contains headless starter and it makes it run headless on CI
        IdePluginInstaller pluginInstaller = new IdePluginInstaller(sandbox.getPluginsDir())
        pluginInstaller.installPlugin(Collections.singletonList(GradleInstrumentation.unpackPlugin("studio-plugin").toPath()))
        def scenarioFile = file("performance.scenarios") << """
            $scenarioName {
                android-studio-sync {
                }
            }
        """

        when:
        CommandExec.RunHandle process = launcher.launchStudio(otherProjectDir)
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
                android-studio-sync {
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
                android-studio-sync {
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
                android-studio-sync {
                    studio-jvm-args = ["-Xmx4104m", "-Xms128m"]
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

    def "doesn't write external annotations to .m2 folder"() {
        given:
        def scenarioFile = file("performance.scenarios") << """
            $scenarioName {
                android-studio-sync {
                }
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
        def mavenRepository = tmpDir.createDir("maven/repository")
        new File("${sandboxDir.absolutePath}/config/options").mkdirs()
        new File("${sandboxDir.absolutePath}/config/options/path.macros.xml").text = """
<application>
  <component name="PathMacrosImpl">
    <macro name="MAVEN_REPOSITORY" value="${mavenRepository.absolutePath}" />
  </component>
</application>"""

        when:
        runBenchmark(scenarioFile, 1, 1)

        then:
        mavenRepository.list() == [] as String[]
    }

    def "can add idea.properties"() {
        given:
        def scenarioFile = file("performance.scenarios") << """
            $scenarioName {
                android-studio-sync {
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
            "--studio-install-dir", ideHome.absolutePath,
            "--studio-sandbox-dir", sandboxDir.absolutePath,
            "--warmups", "$warmups",
            "--iterations", "$iterations",
            *additionalArgs,
            scenarioName
        ]
        new Main().run(*args)
    }
}
