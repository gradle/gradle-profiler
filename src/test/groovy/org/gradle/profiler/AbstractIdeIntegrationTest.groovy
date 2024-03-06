package org.gradle.profiler

import org.gradle.profiler.spock.extensions.ShowAndroidStudioLogsOnFailure

@ShowAndroidStudioLogsOnFailure
abstract class AbstractIdeIntegrationTest extends AbstractProfilerIntegrationTest {

    private def scenarioName = "sync"
    private File ideSandbox
    protected File ideHome

    def setup() {
        ideSandbox = new File("build/sandbox")
        ideHome = new File("build/ide")
        outputDir = new File("build/profiler-output")
        System.setProperty("studio.tests.headless", "true")
    }

    protected def runBenchmark(File scenarioFile, int warmups, int iterations, String... additionalArgs) {
        List<String> args = [
            "--project-dir", projectDir.absolutePath,
            "--output-dir", outputDir.absolutePath,
            "--gradle-version", latestSupportedGradleVersion,
            "--benchmark",
            "--scenario-file", scenarioFile.getAbsolutePath(),
            "--studio-sandbox-dir", ideSandbox.absolutePath,
            "--warmups", "$warmups",
            "--iterations", "$iterations",
            *additionalArgs,
            scenarioName
        ]
        new Main().run(*args)
    }

    protected File scenario(String syncDefinition, String additionalDefinitions = "") {
        file("ide.scenarios") << """
            $scenarioName {
                android-studio-sync {
                    $syncDefinition
                }
                $additionalDefinitions
            }
        """
    }
}
