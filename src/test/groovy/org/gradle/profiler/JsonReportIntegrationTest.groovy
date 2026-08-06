package org.gradle.profiler

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.gradle.profiler.fixtures.AbstractProfilerIntegrationTest

class JsonReportIntegrationTest extends AbstractProfilerIntegrationTest {

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
        normalize(file.text) == """\
{
  "date": "<date>",
  "environment": {
    "profilerVersion": "<profilerVersion>",
    "operatingSystem": "<operatingSystem>"
  },
  "scenarios": [
    {
      "definition": {
        "name": "default",
        "title": "default",
        "displayName": "using Gradle ${minimalSupportedGradleVersion}",
        "buildTool": "Gradle ${minimalSupportedGradleVersion}",
        "tasks": "assemble",
        "version": "${minimalSupportedGradleVersion}",
        "gradleHome": "<gradleHome>",
        "javaHome": "<javaHome>",
        "usesScanPlugin": false,
        "action": "run tasks assemble",
        "cleanup": "do nothing",
        "invoker": "Tooling API",
        "mutators": [],
        "args": [],
        "jvmArgs": [],
        "systemProperties": {},
        "id": "_<uuid>_default_9f67c942"
      },
      "samples": [
        {
          "name": "total execution time",
          "unit": "ms"
        }
      ],
      "iterations": [
        {
          "id": "_<uuid>_default_9f67c942_WARM_UP_1",
          "phase": "WARM_UP",
          "iteration": 1,
          "title": "warm-up build #1",
          "values": {
            "total execution time": "<duration>"
          }
        },
        {
          "id": "_<uuid>_default_9f67c942_MEASURE_1",
          "phase": "MEASURE",
          "iteration": 1,
          "title": "measured build #1",
          "values": {
            "total execution time": "<duration>"
          }
        }
      ]
    }
  ]
}"""
    }

    /**
     * Replaces environment-dependent values with placeholders, keeping the JSON structure intact.
     */
    private static String normalize(String json) {
        JsonObject root = JsonParser.parseString(json).asJsonObject
        root.addProperty("date", "<date>")
        JsonObject environment = root.getAsJsonObject("environment")
        environment.addProperty("profilerVersion", "<profilerVersion>")
        environment.addProperty("operatingSystem", "<operatingSystem>")
        root.getAsJsonArray("scenarios").each { scenario ->
            JsonObject definition = scenario.asJsonObject.getAsJsonObject("definition")
            definition.addProperty("gradleHome", "<gradleHome>")
            definition.addProperty("javaHome", "<javaHome>")
            definition.addProperty("id", normalizeId(definition.get("id").asString))
            // JVM args of the benchmarked build depend on the environment the test runs in
            definition.add("jvmArgs", new JsonArray())
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
