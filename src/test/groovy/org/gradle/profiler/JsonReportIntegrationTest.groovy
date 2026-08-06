package org.gradle.profiler

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.gradle.profiler.fixtures.AbstractProfilerIntegrationTest
import spock.lang.Snapshot
import spock.lang.Snapshotter

class JsonReportIntegrationTest extends AbstractProfilerIntegrationTest {

    @Snapshot(extension = 'json')
    Snapshotter snapshotter

    def "json file is written for benchmarks"() {
        given:
        instrumentedBuildScript()

        when:
        run([
            "--gradle-version", minimalSupportedGradleVersion,
            "--benchmark",
            "assemble"
        ])

        then:
        checkInstrumentedBuildScriptOutputs(minimalSupportedGradleVersion, "assemble")

        def file = new File(outputDir, "benchmark.json")
        file.isFile()
        snapshotter.assertThat(normalize(file.text, minimalSupportedGradleVersion)).matchesSnapshot()
    }

    def "confidence against the first scenario is included when benchmarking multiple scenarios"() {
        given:
        instrumentedBuildScript()
        def scenarioFile = file("performance.scenarios")
        scenarioFile.text = """
            assemble {
                tasks = ["assemble"]
            }
            help {
                tasks = ["help"]
            }
        """

        when:
        run([
            "--gradle-version", minimalSupportedGradleVersion,
            "--benchmark",
            "--scenario-file", scenarioFile.absolutePath
        ])

        then:
        def file = new File(outputDir, "benchmark.json")
        file.isFile()
        snapshotter.assertThat(normalize(file.text, minimalSupportedGradleVersion)).matchesSnapshot()
    }

    /**
     * Replaces environment-dependent values with placeholders, keeping the JSON structure intact.
     */
    private static String normalize(String json, String gradleVersion) {
        JsonObject root = JsonParser.parseString(json).asJsonObject
        root.addProperty("date", "<date>")
        JsonObject environment = root.getAsJsonObject("environment")
        environment.addProperty("profilerVersion", "<profilerVersion>")
        environment.addProperty("operatingSystem", "<operatingSystem>")
        root.getAsJsonArray("scenarios").each { scenario ->
            JsonObject definition = scenario.asJsonObject.getAsJsonObject("definition")
            ["displayName", "buildTool", "version"].each { key ->
                definition.addProperty(key, definition.get(key).asString.replace(gradleVersion, "<gradleVersion>"))
            }
            definition.addProperty("gradleHome", "<gradleHome>")
            definition.addProperty("javaHome", "<javaHome>")
            definition.addProperty("id", normalizeId(definition.get("id").asString))
            // JVM args of the benchmarked build depend on the environment the test runs in
            definition.add("jvmArgs", new JsonArray())
            scenario.asJsonObject.getAsJsonArray("samples").each { sample ->
                JsonObject stats = sample.asJsonObject.getAsJsonObject("stats")
                if (stats != null) {
                    new ArrayList<>(stats.keySet()).each { stats.addProperty(it, "<duration>") }
                }
                JsonObject confidence = sample.asJsonObject.getAsJsonObject("confidence")
                if (confidence != null) {
                    confidence.addProperty("baseline", normalizeId(confidence.get("baseline").asString))
                    confidence.addProperty("value", "<confidence>")
                }
            }
            scenario.asJsonObject.getAsJsonArray("iterations").each { iteration ->
                JsonObject iterationJson = iteration.asJsonObject
                iterationJson.addProperty("id", normalizeId(iterationJson.get("id").asString))
                JsonObject values = iterationJson.getAsJsonObject("values")
                new ArrayList<>(values.keySet()).each { values.addProperty(it, "<duration>") }
            }
        }
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root)
    }

    private static String normalizeId(String id) {
        return id.replaceAll(/[0-9a-f]{8}_[0-9a-f]{4}_[0-9a-f]{4}_[0-9a-f]{4}_[0-9a-f]{12}/, "<uuid>")
    }
}
