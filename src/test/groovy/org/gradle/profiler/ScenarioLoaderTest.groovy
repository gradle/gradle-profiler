package org.gradle.profiler

import org.gradle.tooling.model.idea.IdeaProject
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import static org.gradle.profiler.ScenarioLoader.loadScenarios

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

    private settings(Invoker invoker = Invoker.Cli) {
        new InvocationSettings(projectDir, Profiler.NONE, true, outputDir, invoker, false, scenarioFile, [], [], [:], gradleUserHomeDir, 1, 1)
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

    def "can load single Android studio sync scenario"() {
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
            
            include file("${otherConf.absolutePath.replace((char)'\\', (char) '/')}")
        """
        def scenarios = loadScenarios(scenarioFile, settings, Mock(GradleBuildConfigurationReader))
        expect:
        scenarios*.name == ["alma"]
        (scenarios[0] as GradleScenarioDefinition).action.tasks == ["alma"]
    }

    def "can load Bazel scenario"() {
        def settings = settings(Invoker.Bazel)

        scenarioFile << """
            default {
                bazel {
                    targets = ["help"]
                }
            }
        """
        def scenarios = loadScenarios(scenarioFile, settings, Mock(GradleBuildConfigurationReader))
        expect:
        scenarios*.name == ["default"]
        (scenarios[0] as BazelScenarioDefinition).targets == ["help"]
    }

    def "can load Buck scenario"() {
        def settings = settings(Invoker.Buck)

        scenarioFile << """
            default {
                buck {
                    targets = ["help"]
                }
            }
        """
        def scenarios = loadScenarios(scenarioFile, settings, Mock(GradleBuildConfigurationReader))
        expect:
        scenarios*.name == ["default"]
        (scenarios[0] as BuckScenarioDefinition).targets == ["help"]
    }

    def "can load Maven scenario"() {
        def settings = settings(Invoker.Maven)

        scenarioFile << """
            default {
                maven {
                    targets = ["help"]
                }
            }
        """
        def scenarios = loadScenarios(scenarioFile, settings, Mock(GradleBuildConfigurationReader))
        expect:
        scenarios*.name == ["default"]
        (scenarios[0] as MavenScenarioDefinition).targets == ["help"]
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
}
