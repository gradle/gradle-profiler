package org.gradle.profiler

import org.gradle.profiler.fixtures.AbstractProfilerIntegrationTest
import org.gradle.profiler.fixtures.file.LeaksFileHandles

// https://github.com/gradle/gradle-profiler/issues/714
@LeaksFileHandles({ OperatingSystem.isWindows() && it.name == "native-platform.dll" })
class ScenarioMatricesIntegrationTest extends AbstractProfilerIntegrationTest {

    def "synthesized scenarios are selectable by default, by name, and via auto-generated group"() {
        given:
        def scenarioFile = writeScenarioFile("""
            workflowA { tasks = ["help"] }
            workflowB { tasks = ["help"] }
            cold { daemon = cold }
            warm { daemon = warm }
            standalone { tasks = ["help"] }
            scenario-matrices {
                matrix {
                    dimensions = [
                        { name = workflow, scenarios = [workflowA, workflowB] }
                        { name = daemon,   scenarios = [cold, warm] }
                    ]
                }
            }
        """)
        buildFile.text = "apply plugin: BasePlugin"

        when: "no selection — every synthesized and component scenario runs"
        runProfiler(scenarioFile)

        then:
        outputFile("matrix_workflowA_cold").directory
        outputFile("matrix_workflowA_warm").directory
        outputFile("matrix_workflowB_cold").directory
        outputFile("matrix_workflowB_warm").directory
        outputFile("workflowA").directory
        outputFile("workflowB").directory
        outputFile("standalone").directory

        when: "--group selects the fix-one auto-group"
        resetOutputDir()
        runProfiler(scenarioFile, "--group", "matrix_workflowA")

        then:
        outputFile("matrix_workflowA_cold").directory
        outputFile("matrix_workflowA_warm").directory
        !outputFile("matrix_workflowB_cold").directory
        !outputFile("matrix_workflowB_warm").directory

        when: "positional targets mix a synthesized scenario and a hand-written one"
        resetOutputDir()
        runProfiler(scenarioFile, "matrix_workflowA_cold", "standalone")

        then:
        outputFile("matrix_workflowA_cold").directory
        outputFile("standalone").directory
        !outputFile("matrix_workflowA_warm").directory
        !outputFile("matrix_workflowB_cold").directory
    }

    def "a fix-all-but-one auto-group runs the variants of a single dimension"() {
        given:
        def scenarioFile = writeScenarioFile("""
            workflowA { tasks = ["help"] }
            workflowB { tasks = ["help"] }
            tagSmoke   { system-properties { "test.tag" = "smoke"   } }
            tagRelease { system-properties { "test.tag" = "release" } }
            ccOn  { system-properties { "org.gradle.configuration-cache" = "true"  } }
            ccOff { system-properties { "org.gradle.configuration-cache" = "false" } }
            scenario-matrices {
                matrix {
                    dimensions = [
                        { name = workflow, scenarios = [workflowA, workflowB] }
                        { name = tag,      scenarios = [tagSmoke, tagRelease] }
                        { name = cc,       scenarios = [ccOn, ccOff] }
                    ]
                }
            }
        """)
        buildFile.text = "apply plugin: BasePlugin"

        when:
        runProfiler(scenarioFile, "--group", "matrix_workflowA_ccOn")

        then:
        outputFile("matrix_workflowA_tagSmoke_ccOn").directory
        outputFile("matrix_workflowA_tagRelease_ccOn").directory
        !outputFile("matrix_workflowA_tagSmoke_ccOff").directory
        !outputFile("matrix_workflowB_tagRelease_ccOn").directory
    }

    def "system-properties merge across picked scenarios and reach the build (maps merge recursively)"() {
        given:
        def scenarioFile = writeScenarioFile('''
            left {
                tasks = ["help"]
                system-properties {
                    "shared.key" = "left-wins-if-alone"
                    "left-only"  = "L"
                }
            }
            right {
                system-properties {
                    "shared.key" = "right-overrides"
                    "right-only" = "R"
                }
            }
            scenario-matrices {
                matrix {
                    dimensions = [
                        { name = l, scenarios = [left] }
                        { name = r, scenarios = [right] }
                    ]
                }
            }
        ''')
        buildFile.text = """
            apply plugin: BasePlugin
            println "<shared: " + System.getProperty("shared.key") + ">"
            println "<left-only: " + System.getProperty("left-only") + ">"
            println "<right-only: " + System.getProperty("right-only") + ">"
        """

        when:
        runProfiler(scenarioFile, "matrix_left_right")

        then:
        logFile.find("<shared: right-overrides>").size() >= 1
        logFile.find("<left-only: L>").size() >= 1
        logFile.find("<right-only: R>").size() >= 1
        !logFile.find("<shared: left-wins-if-alone>")
    }

    def "later dimension's scenario fully replaces earlier scenario's array values (arrays do not merge)"() {
        given:
        def scenarioFile = writeScenarioFile('''
            left  { tasks = ["assemble", "check"] }
            right { tasks = ["help"] }
            scenario-matrices {
                matrix {
                    dimensions = [
                        { name = l, scenarios = [left] }
                        { name = r, scenarios = [right] }
                    ]
                }
            }
        ''')
        buildFile.text = """
            apply plugin: BasePlugin
            println "<tasks: " + gradle.startParameter.taskNames + ">"
        """

        when:
        runProfiler(scenarioFile, "matrix_left_right")

        then:
        logFile.find("<tasks: [help]>").size() >= 1
        !logFile.find("<tasks: [assemble, check, help]>")
        !logFile.find("<tasks: [assemble, check]>")
    }

    def "later dimension's scenario overrides earlier scenario's primitive (daemon mode)"() {
        given:
        def scenarioFile = writeScenarioFile('''
            warmFirst {
                tasks = ["help"]
                daemon = warm
            }
            coldSecond { daemon = cold }
            scenario-matrices {
                matrix {
                    dimensions = [
                        { name = a, scenarios = [warmFirst] }
                        { name = b, scenarios = [coldSecond] }
                    ]
                }
            }
        ''')
        buildFile.text = "apply plugin: BasePlugin"

        when:
        runProfiler(scenarioFile, "matrix_warmFirst_coldSecond")

        then:
        // The profile log surfaces the resolved invoker for each scenario via "Run using:".
        logFile.find("Run using: Tooling API with cold daemon").size() >= 1
        !logFile.find("Run using: Tooling API with warm daemon")
    }

    def "name and title concatenation honor #separatorKind separators"() {
        given:
        def scenarioFile = writeScenarioFile("""
            workflowA {
                title = "Workflow A"
                tasks = ["help"]
            }
            coldDaemon {
                title = "Cold Daemon"
                daemon = cold
            }
            standalone { tasks = ["help"] }
            scenario-matrices {
                matrix {
                    title = "Matrix"
                    ${separatorConfig}
                    dimensions = [
                        { name = workflow, scenarios = [workflowA] }
                        { name = daemon,   scenarios = [coldDaemon] }
                    ]
                }
            }
        """)
        buildFile.text = "apply plugin: BasePlugin"

        when:
        // Run with a second scenario so per-scenario subdirs are created (the profiler uses outputDir directly when only one scenario runs).
        runProfiler(scenarioFile, expectedScenarioName, "standalone")

        then:
        outputFile(expectedScenarioName).directory
        logFile.find("Running scenario ${expectedTitle}").size() >= 1

        where:
        separatorKind | separatorConfig   | expectedScenarioName          | expectedTitle
        "default"     | ""                | "matrix_workflowA_coldDaemon" | "Matrix. Workflow A. Cold Daemon"
        "custom"      | CUSTOM_SEPARATORS | "matrix-workflowA-coldDaemon" | "Matrix | Workflow A | Cold Daemon"
    }

    def "--dump-scenarios renders both synthesized and picked scenarios with composed values"() {
        given:
        def scenarioFile = writeScenarioFile('''
            workflowA {
                title = "Workflow A"
                tasks = ["assemble"]
                system-properties {
                    "x" = "1"
                }
            }
            coldDaemon {
                title = "Cold Daemon"
                daemon = cold
                system-properties {
                    "y" = "2"
                }
            }
            scenario-matrices {
                matrix {
                    title = "Matrix"
                    dimensions = [
                        { name = workflow, scenarios = [workflowA] }
                        { name = daemon,   scenarios = [coldDaemon] }
                    ]
                }
            }
        ''')

        when:
        dumpScenarios(scenarioFile)

        then:
        def out = output
        // Synthesized scenario carries merged values from both scenarios.
        out.contains("matrix_workflowA_coldDaemon {")
        out.contains('title="Matrix. Workflow A. Cold Daemon"')
        out.contains("daemon=cold")
        out.contains("assemble")
        out.contains('x="1"')
        out.contains('y="2"')
        // Picked scenarios remain individually dumpable.
        out.contains("workflowA {")
        out.contains("coldDaemon {")
        // The scenario-matrices block is consumed by expansion and does not appear in the dump.
        !out.contains("scenario-matrices")
        !out.contains("dimensions")
    }

    def "synthesized scenarios are referenceable from #selectionMechanism"() {
        given:
        def scenarioFile = writeScenarioFile("""
            a { tasks = ["help"] }
            b { tasks = ["help"] }
            other { tasks = ["help"] }
            ${selectionConfig}
            scenario-matrices {
                matrix {
                    dimensions = [
                        { name = l, scenarios = [a] }
                        { name = r, scenarios = [b] }
                    ]
                }
            }
        """)
        buildFile.text = "apply plugin: BasePlugin"

        when:
        runProfiler(scenarioFile, extraArgs as String[])

        then:
        outputFile("matrix_a_b").directory
        outputFile("other").directory
        !outputFile("a").directory
        !outputFile("b").directory

        where:
        selectionMechanism  | selectionConfig                                       | extraArgs
        "default-scenarios" | 'default-scenarios = ["matrix_a_b", "other"]'         | []
        "scenario-groups"   | 'scenario-groups { smoke = ["matrix_a_b", "other"] }' | ["--group", "smoke"]
    }

    def "--dump-scenarios reports a clear error for malformed scenario-matrices"() {
        given:
        def scenarioFile = writeScenarioFile("""
            a { tasks = ["a"] }
            scenario-matrices {
                matrix {
                    dimensions = [
                        { name = l, scenarios = [a] }
                        { name = r, scenarios = [does_not_exist] }
                    ]
                }
            }
        """)

        when:
        dumpScenarios(scenarioFile)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("does_not_exist")
        ex.message.contains("matrix")
    }

    // --- helpers ---

    private File writeScenarioFile(String body) {
        def f = file("benchmark.conf")
        f.text = body
        return f
    }

    /** Run the profiler in dry-run mode against the latest supported Gradle. */
    private void runProfiler(File scenarioFile, String... extraArgs) {
        def args = [
            "--project-dir", projectDir.absolutePath,
            "--output-dir", outputDir.absolutePath,
            "--gradle-version", latestSupportedGradleVersion,
            "--scenario-file", scenarioFile.absolutePath,
            "--benchmark",
            "--dry-run",
        ]
        args.addAll(extraArgs as List)
        new Main().run(args as String[])
    }

    /** Clear the output directory between runs in tests that exercise multiple invocations. */
    private void resetOutputDir() {
        outputDir.deleteDir()
        outputDir.mkdirs()
    }

    /** Invoke --dump-scenarios; no build is executed. */
    private static void dumpScenarios(File scenarioFile, String... extraArgs) {
        def args = [
            "--benchmark",
            "--scenario-file", scenarioFile.absolutePath,
            "--dump-scenarios",
        ]
        args.addAll(extraArgs as List)
        new Main().run(args as String[])
    }

    private static final String CUSTOM_SEPARATORS = '''
        name-separator = "-"
        title-separator = " | "
    '''
}
