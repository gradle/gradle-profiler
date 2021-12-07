package org.gradle.profiler

import org.gradle.profiler.studio.launcher.LaunchConfiguration
import org.gradle.profiler.studio.launcher.LauncherConfigurationParser
import org.gradle.profiler.studio.tools.StudioFinder
import org.gradle.profiler.studio.tools.StudioPluginInstaller
import org.gradle.profiler.studio.tools.StudioSandboxCreator
import spock.lang.Requires

class AndroidStudioIntegrationTest extends AbstractProfilerIntegrationTest {

    File sandboxDir
    File studioHome
    String scenarioName

    def setup() {
        sandboxDir = tmpDir.newFolder('sandbox')
        studioHome = StudioFinder.findStudioHome()
        scenarioName = "scenario"
    }

    @Requires({ StudioFinder.findStudioHome() })
    def "benchmarks Android Studio sync with latest gradle version"() {
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
        logFile.find("Gradle invocation 2 has completed in").size() == 0
        logFile.find("Full sync has completed in").size() == 5
        logFile.find("and it SUCCEEDED").size() == 5
        logFile.find("* Cleaning Android Studio cache, this will require a restart...").size() == 0
        logFile.find("* Starting Android Studio").size() == 1

        and:
        File benchmarkCsv = outputDir.listFiles().find { it.name.matches("benchmark.csv") }
        benchmarkCsv.text.contains("value,total execution time,Gradle total execution time,IDE execution time")
    }

    @Requires({ StudioFinder.findStudioHome() })
    def "benchmarks Android Studio sync for project with buildSrc"() {
        // This tests that Android Studio can call Gradle multiple times during a sync
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
        logFile.find("Gradle invocation 2 has completed in").size() == 2
        def firstDurations = (logFile.text =~ /Gradle invocation 1 has completed in: (\d+)ms/)
            .findAll()
            .collect { it[1] as Integer }
        def secondDurations = (logFile.text =~ /Gradle invocation 2 has completed in: (\d+)ms/)
            .findAll()
            .collect { it[1] as Integer }
        logFile.find("Full Gradle execution time: ${firstDurations[0] + secondDurations[0]}ms").size() == 1
        logFile.find("Full Gradle execution time: ${firstDurations[1] + secondDurations[1]}ms").size() == 1
        logFile.find("Full sync has completed in").size() == 2
        logFile.find("and it SUCCEEDED").size() == 2
        logFile.find("* Cleaning Android Studio cache, this will require a restart...").size() == 0
        logFile.find("* Starting Android Studio").size() == 1

        and:
        File benchmarkCsv = outputDir.listFiles().find { it.name.matches("benchmark.csv") }
        benchmarkCsv.text.contains("value,total execution time,Gradle execution time #1,Gradle execution time #2,Gradle total execution time,IDE execution time")
    }

    @Requires({ StudioFinder.findStudioHome() })
    def "benchmarks Android Studio sync by cleaning ide cache before build"() {
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

    @Requires({ StudioFinder.findStudioHome() })
    def "benchmarks Android Studio sync by cleaning ide cache before scenario"() {
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

    @Requires({ StudioFinder.findStudioHome() })
    def "detects if two Android Studio processes are running in the same sandbox"() {
        given:
        File otherStudioProjectDir = tmpDir.newFolder('project')
        // We have to install plugin so also the first Studio process is run in the headless mode.
        // We install plugin directory to a different "plugins-2" directory for first process otherwise cleaning plugin directory at start of second process fails on Windows.
        StudioSandboxCreator.StudioSandbox sandbox = StudioSandboxCreator.createSandbox(sandboxDir.toPath(), "plugins-2")
        LaunchConfiguration launchConfiguration = new LauncherConfigurationParser(studioHome.toPath(), sandbox).calculate()
        StudioPluginInstaller pluginInstaller = new StudioPluginInstaller(launchConfiguration.getStudioPluginsDir())
        pluginInstaller.installPlugin(launchConfiguration.getStudioPluginJars())
        def scenarioFile = file("performance.scenarios") << """
            $scenarioName {
                android-studio-sync {
                }
            }
        """

        when:
        CommandExec.RunHandle process = launchConfiguration.launchStudio(otherStudioProjectDir)
        runBenchmark(scenarioFile, 1, 1)

        then:
        def e = thrown(Main.ScenarioFailedException)
        e.getCause().message == "Timeout waiting for incoming connection from start-detector."
        logFile.containsOne("* ERROR")
        logFile.containsOne("* Could not connect to Android Studio process started by the gradle-profiler.")
        logFile.containsOne("* This might indicate that you are already running an Android Studio process in the same sandbox.")
        logFile.containsOne("* Stop Android Studio manually in the used sandbox or use a different sandbox with --studio-sandbox-dir to isolate the process.")

        cleanup:
        process.kill()
    }

    @Requires({ StudioFinder.findStudioHome() })
    def "allows two Android Studio processes in different sandboxes"() {
        given:
        File sandboxDir1 = tmpDir.newFolder('sandbox1')
        // We create a different folder for project for the other process,
        // since if Android Studio writes to same project at the same time, it can fail
        File otherStudioProjectDir = tmpDir.newFolder('project')
        StudioSandboxCreator.StudioSandbox sandbox = StudioSandboxCreator.createSandbox(sandboxDir1.toPath())
        LaunchConfiguration launchConfiguration = new LauncherConfigurationParser(studioHome.toPath(), sandbox).calculate()
        // We have to install plugin so also the first Studio process can be run in headless mode mode
        StudioPluginInstaller pluginInstaller = new StudioPluginInstaller(launchConfiguration.getStudioPluginsDir())
        pluginInstaller.installPlugin(launchConfiguration.getStudioPluginJars())
        def scenarioFile = file("performance.scenarios") << """
            $scenarioName {
                android-studio-sync {
                }
            }
        """

        when:
        CommandExec.RunHandle process = launchConfiguration.launchStudio(otherStudioProjectDir)
        runBenchmark(scenarioFile, 1, 1)

        then:
        logFile.find("Gradle invocation 1 has completed in").size() == 2
        logFile.find("Full sync has completed in").size() == 2

        cleanup:
        process.kill()
    }


    @Requires({ StudioFinder.findStudioHome() })
    def "fails fast if Android Studio sync fails"() {
        given:
        def scenarioFile = file("performance.scenarios") << """
            $scenarioName {
                android-studio-sync {
                }
            }
        """
        def markerFolder = tmpDir.newFolder()
        buildFile << """
            File markerFile = new File('${markerFolder.absolutePath.replace("\\", "\\\\")}', 'marker.txt')
            if (markerFile.exists()) {
                throw new RuntimeException('File exists')
            } else {
                markerFile.createNewFile()
            }
        """

        when:
        runBenchmark(scenarioFile, 1, 2)

        then:
        def e = thrown(Main.ScenarioFailedException)
        println e.getCause().message
        e.getCause().message == "Gradle sync has failed with error message: 'File exists\n\nConsult IDE log for more details (Help | Show Log)'. Full Android Studio logs can be found in: '${new File(sandboxDir.absolutePath + "/logs/idea.log")}'."
        logFile.find("Full Gradle execution time").size() == 1
        logFile.find("Full sync has completed in").size() == 1
        logFile.find("and it FAILED").size() == 1
    }

    def runBenchmark(File scenarioFile, int warmups, int iterations) {
        new Main().run(
            "--project-dir", projectDir.absolutePath,
            "--output-dir", outputDir.absolutePath,
            "--gradle-version", latestSupportedGradleVersion,
            "--benchmark",
            "--scenario-file", scenarioFile.getAbsolutePath(),
            "--studio-install-dir", studioHome.absolutePath,
            "--studio-sandbox-dir", sandboxDir.absolutePath,
            "--warmups", "$warmups",
            "--iterations", "$iterations",
            scenarioName)
    }
}
