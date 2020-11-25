package org.gradle.profiler

class HeapDumpIntegrationTest extends AbstractProfilerIntegrationTest {
    def "generates a heap dump when run with tooling API and warm daemon"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", latestSupportedGradleVersion, "--profile", "heap-dump", "assemble")

        then:
        // 2 warm ups and 1 measured build
        new File(outputDir, "${latestSupportedGradleVersion}-heap-3.hprof").file
        !new File(outputDir, "${latestSupportedGradleVersion}-heap-4.hprof").file
    }

    def "generates a heap dump when run with tooling API and warm daemon and multiple iterations"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", latestSupportedGradleVersion, "--profile", "heap-dump", "-iterations", "2", "assemble")

        then:
        // 2 warm ups and 2 measured builds
        new File(outputDir, "${latestSupportedGradleVersion}-heap-3.hprof").file
        new File(outputDir, "${latestSupportedGradleVersion}-heap-4.hprof").file
    }
}
