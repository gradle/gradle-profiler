package org.gradle.profiler

import com.google.gson.Gson
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
        assert file.isFile()
        def report = new Gson().fromJson(file.text, Map.class)
        assert (((report.get("scenarios") as List).get(0) as Map).get("iterations") as List).size() == 2
    }
}
