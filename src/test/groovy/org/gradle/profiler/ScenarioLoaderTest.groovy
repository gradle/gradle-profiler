package org.gradle.profiler

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import static org.gradle.profiler.ScenarioLoader.*

class ScenarioLoaderTest extends Specification {
    @Rule TemporaryFolder tmpDir = new TemporaryFolder()

    File projectDir
    File gradleUserHomeDir
    File outputDir
    File scenarioFile

    def setup() {
        projectDir = tmpDir.newFolder()
        outputDir = tmpDir.newFolder()
        scenarioFile = tmpDir.newFile()
    }

    def "can load single scenario"() {
        def settings = new InvocationSettings(projectDir, Profiler.NONE, true, outputDir, Invoker.Cli, false, scenarioFile, [], [], [:], gradleUserHomeDir, 1, 1)

        scenarioFile << """
            default {
                tasks = ["help"]
            }
        """
        def scenarios = loadScenarios(scenarioFile, settings, Mock(GradleVersionInspector))
        expect:
        scenarios*.name == ["default"]
        (scenarios[0] as GradleScenarioDefinition).tasks == ["help"]
    }

    def "loads default scenarios only"() {
        def settings = new InvocationSettings(projectDir, Profiler.NONE, true, outputDir, Invoker.Cli, false, scenarioFile, [], [], [:], gradleUserHomeDir, 1, 1)

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
        def scenarios = loadScenarios(scenarioFile, settings, Mock(GradleVersionInspector))
        expect:
        scenarios*.name == ["alma", "bela"]
        (scenarios[0] as GradleScenarioDefinition).tasks == ["alma"]
        (scenarios[1] as GradleScenarioDefinition).tasks == ["bela"]
    }

    def "loads included config"() {
        def settings = new InvocationSettings(projectDir, Profiler.NONE, true, outputDir, Invoker.Cli, false, scenarioFile, [], [], [:], gradleUserHomeDir, 1, 1)

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
            
            include file("${otherConf.absolutePath.replace((char)'\\', (char) '/')}")
        """
        def scenarios = loadScenarios(scenarioFile, settings, Mock(GradleVersionInspector))
        expect:
        scenarios*.name == ["alma"]
        (scenarios[0] as GradleScenarioDefinition).tasks == ["alma"]
    }

    def "can load Bazel scenario"() {
        def settings = new InvocationSettings(projectDir, Profiler.NONE, true, outputDir, Invoker.Bazel, false, scenarioFile, [], [], [:], gradleUserHomeDir, 1, 1)

        scenarioFile << """
            default {
                bazel {
                    targets = ["help"]
                }
            }
        """
        def scenarios = loadScenarios(scenarioFile, settings, Mock(GradleVersionInspector))
        expect:
        scenarios*.name == ["default"]
        (scenarios[0] as BazelScenarioDefinition).targets == ["help"]
    }

    def "can load Buck scenario"() {
        def settings = new InvocationSettings(projectDir, Profiler.NONE, true, outputDir, Invoker.Buck, false, scenarioFile, [], [], [:], gradleUserHomeDir, 1, 1)

        scenarioFile << """
            default {
                buck {
                    targets = ["help"]
                }
            }
        """
        def scenarios = loadScenarios(scenarioFile, settings, Mock(GradleVersionInspector))
        expect:
        scenarios*.name == ["default"]
        (scenarios[0] as BuckScenarioDefinition).targets == ["help"]
    }

    def "can load Maven scenario"() {
        def settings = new InvocationSettings(projectDir, Profiler.NONE, true, outputDir, Invoker.Maven, false, scenarioFile, [], [], [:], gradleUserHomeDir, 1, 1)

        scenarioFile << """
            default {
                maven {
                    targets = ["help"]
                }
            }
        """
        def scenarios = loadScenarios(scenarioFile, settings, Mock(GradleVersionInspector))
        expect:
        scenarios*.name == ["default"]
        (scenarios[0] as MavenScenarioDefinition).targets == ["help"]
    }

    def "can load scenario with multiple files for a single mutation"() {
        def settings = new InvocationSettings(projectDir, Profiler.NONE, true, outputDir, Invoker.Cli, false, scenarioFile, [], [], [:], gradleUserHomeDir, 1, 1)
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
        def scenarios = loadScenarios(scenarioFile, settings, Mock(GradleVersionInspector))

        expect:
        scenarios*.name == ["default"]
    }
}
