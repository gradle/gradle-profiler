package org.gradle.profiler

class ToolingApiIntegrationTest extends AbstractProfilerIntegrationTest {
    def "runs benchmarks fetching tooling model"() {
        given:
        def scenarioFile = file("benchmark.conf")
        scenarioFile.text = """
ideaModel {
    versions = ["$minimalSupportedGradleVersion", "$latestSupportedGradleVersion"]
    tooling-api {
        model = "org.gradle.tooling.model.idea.IdeaProject"
    }
}
"""

        buildFile.text = """
apply plugin: BasePlugin
println "<gradle-version: " + gradle.gradleVersion + ">"
println "<tasks: " + gradle.startParameter.taskNames + ">"
println "<daemon: " + gradle.services.get(org.gradle.internal.environment.GradleBuildEnvironment).longLivingProcess + ">"
plugins.withId("idea") {
    // most likely due to IDEA model builder
    println("<idea>")
}
"""

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--scenario-file", scenarioFile.absolutePath,
            "--benchmark", "ideaModel")

        then:
        // Probe version, 6 warm up, 10 builds
        logFile.find("<gradle-version: $minimalSupportedGradleVersion>").size() == 17
        logFile.find("<gradle-version: $latestSupportedGradleVersion").size() == 17
        logFile.find("<daemon: true").size() == 17 * 2
        logFile.find("<tasks: []>").size() == 16 * 2
        logFile.find("<idea>").size() == 16 * 2

        logFile.containsOne("* Running scenario ideaModel using Gradle $minimalSupportedGradleVersion (scenario 1/2)")
        logFile.containsOne("* Running scenario ideaModel using Gradle $latestSupportedGradleVersion (scenario 2/2)")

        def lines = resultFile.lines
        lines.size() == totalLinesForExecutions(16)
        lines.get(0) == "scenario,ideaModel,ideaModel"
        lines.get(1) == "version,Gradle $minimalSupportedGradleVersion,Gradle $latestSupportedGradleVersion"
        lines.get(2) == "tasks,model IdeaProject,model IdeaProject"
        lines.get(3) == "value,total execution time,total execution time"
    }

    def "runs benchmarks running tooling action"() {
        given:
        def scenarioFile = file("benchmark.conf")
        scenarioFile.text = """
fetchModel {
    versions = ["$minimalSupportedGradleVersion", "$latestSupportedGradleVersion"]
    tooling-api {
        action = "org.gradle.profiler.toolingapi.FetchProjectPublications"
    }
}
"""

        buildFile.text = """
apply plugin: BasePlugin
println "<gradle-version: " + gradle.gradleVersion + ">"
println "<tasks: " + gradle.startParameter.taskNames + ">"
println "<daemon: " + gradle.services.get(org.gradle.internal.environment.GradleBuildEnvironment).longLivingProcess + ">"
"""

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--scenario-file", scenarioFile.absolutePath,
            "--benchmark", "fetchModel")

        then:
        // Probe version, 6 warm up, 10 builds
        logFile.find("<gradle-version: $minimalSupportedGradleVersion>").size() == 17
        logFile.find("<gradle-version: $latestSupportedGradleVersion").size() == 17
        logFile.find("<daemon: true").size() == 17 * 2
        logFile.find("<tasks: []>").size() == 16 * 2

        logFile.containsOne("* Running scenario fetchModel using Gradle $minimalSupportedGradleVersion (scenario 1/2)")
        logFile.containsOne("* Running scenario fetchModel using Gradle $latestSupportedGradleVersion (scenario 2/2)")

        def lines = resultFile.lines
        lines.size() == totalLinesForExecutions(16)
        lines.get(0) == "scenario,fetchModel,fetchModel"
        lines.get(1) == "version,Gradle $minimalSupportedGradleVersion,Gradle $latestSupportedGradleVersion"
        lines.get(2) == "tasks,action FetchProjectPublications,action FetchProjectPublications"
        lines.get(3) == "value,total execution time,total execution time"
    }

    def "profiles fetching tooling model using JFR"() {
        given:
        def scenarioFile = file("benchmark.conf")
        scenarioFile.text = """
ideaModel {
    versions = ["$minimalSupportedGradleVersion", "$latestSupportedGradleVersion"]
    tooling-api {
        model = "org.gradle.tooling.model.idea.IdeaProject"
    }
}
"""

        buildFile.text = """
apply plugin: BasePlugin
println "<gradle-version: " + gradle.gradleVersion + ">"
println "<tasks: " + gradle.startParameter.taskNames + ">"
println "<daemon: " + gradle.services.get(org.gradle.internal.environment.GradleBuildEnvironment).longLivingProcess + ">"
plugins.withId("idea") {
    // most likely due to IDEA model builder
    println("<idea>")
}
"""

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--scenario-file", scenarioFile.absolutePath,
            "--profile", "jfr", "ideaModel")

        then:
        // Probe version, 2 warm up, 1 profiled build
        logFile.find("<gradle-version: $minimalSupportedGradleVersion>").size() == 4
        logFile.find("<gradle-version: $latestSupportedGradleVersion>").size() == 4
        logFile.find("<daemon: true").size() == 8
        logFile.find("<tasks: []>").size() == 6
        logFile.find("<idea>").size() == 6

        logFile.containsOne("* Running scenario ideaModel using Gradle $minimalSupportedGradleVersion (scenario 1/2)")
        logFile.containsOne("* Running scenario ideaModel using Gradle $latestSupportedGradleVersion (scenario 2/2)")

        def profileFile = new File(outputDir, "$minimalSupportedGradleVersion/ideaModel-${minimalSupportedGradleVersion}.jfr")
        profileFile.isFile()
    }
}
