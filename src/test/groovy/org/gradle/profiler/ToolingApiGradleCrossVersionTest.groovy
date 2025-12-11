package org.gradle.profiler


import org.gradle.profiler.fixtures.compatibility.gradle.AbstractGradleCrossVersionTest

class ToolingApiGradleCrossVersionTest extends AbstractGradleCrossVersionTest {

    def setup() {
        instrumentedBuildScript()
        buildFile << """
            plugins.withId("idea") {
                // most likely due to IDEA model builder
                println("<idea>")
            }
        """
    }

    def "runs benchmarks fetching tooling model"() {
        given:
        def scenarioFile = file("benchmark.conf")
        scenarioFile.text = """
fetchModel {
    versions = ["$gradleVersion"]
    tooling-api {
        model = "org.gradle.tooling.model.idea.IdeaProject"
    }
}
"""

        when:
        run([
            "--benchmark", "--scenario-file", scenarioFile.absolutePath,
            "fetchModel"
        ])

        then:
        checkInstrumentedBuildScriptOutputs(gradleVersion)
        logFile.find("<idea>").size() == warmups + iterations

        logFile.containsOne("* Running scenario fetchModel using Gradle $gradleVersion (scenario 1/1)")

        def lines = resultFile.lines
        lines.size() == totalLinesForExecutions(warmups + iterations)
        lines.get(0) == "scenario,fetchModel"
        lines.get(1) == "version,Gradle $gradleVersion"
        lines.get(2) == "tasks,model IdeaProject"
        lines.get(3) == "value,total execution time"
    }

    def "runs benchmarks running tooling action"() {
        given:
        def scenarioFile = file("benchmark.conf")
        scenarioFile.text = """
fetchModel {
    versions = ["$gradleVersion"]
    tooling-api {
        action = "org.gradle.profiler.toolingapi.FetchProjectPublications"
    }
}
"""

        when:
        run([
            "--benchmark", "--scenario-file", scenarioFile.absolutePath,
            "fetchModel"
        ])

        then:
        checkInstrumentedBuildScriptOutputs(gradleVersion)

        logFile.containsOne("* Running scenario fetchModel using Gradle $gradleVersion (scenario 1/1)")

        def lines = resultFile.lines
        lines.size() == totalLinesForExecutions(warmups + iterations)
        lines.get(0) == "scenario,fetchModel"
        lines.get(1) == "version,Gradle $gradleVersion"
        lines.get(2) == "tasks,action FetchProjectPublications"
        lines.get(3) == "value,total execution time"
    }

    def "profiles fetching tooling model using JFR"() {
        given:
        def scenarioFile = file("benchmark.conf")
        scenarioFile.text = """
fetchModel {
    versions = ["$gradleVersion"]
    tooling-api {
        model = "org.gradle.tooling.model.idea.IdeaProject"
    }
}
"""

        when:
        run([
            "--scenario-file", scenarioFile.absolutePath,
            "--profile", "jfr",
            "fetchModel"
        ])

        then:
        checkInstrumentedBuildScriptOutputs(gradleVersion)
        logFile.find("<idea>").size() == warmups + iterations

        logFile.containsOne("* Running scenario fetchModel using Gradle $gradleVersion (scenario 1/1)")

        def profileFile = new File(outputDir, "fetchModel-${gradleVersion}.jfr")
        profileFile.isFile()
    }
}
