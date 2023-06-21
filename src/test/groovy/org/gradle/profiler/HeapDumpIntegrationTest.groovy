package org.gradle.profiler

class HeapDumpIntegrationTest extends AbstractProfilerIntegrationTest {
    def "can generate heap dump"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath,
            "--output-dir", outputDir.absolutePath,
            "--gradle-version", latestSupportedGradleVersion,
            "--iterations", "0",
            "--warmups", "1",
            "--profile", "heap-dump", "assemble")

        then:
        // just run one build
        assertIsHprof("heap-1.hprof")
        assertIsJmap("heap-1.jmap")
    }

    private void assertIsHprof(String partialName) {
        assertHasFile(partialName)
        // Check the header of the file to see if it looks like a hprof
        byte[] header = new byte[12]
        mkFile(partialName).withInputStream {
            it.read(header)
        }
        assert new String(header) == "JAVA PROFILE"
    }

    private void assertIsJmap(String partialName) {
        assertHasFile(partialName)
        def lines = mkFile(partialName).readLines().grep { !it.empty }

        def firstLine = lines.first()
        assert firstLine.startsWith(" num     #instances         #bytes")

        def lastLine = lines.last()
        assert lastLine.startsWith("Total ")
    }

    def "generates a heap dump when run with tooling API and warm daemon"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", latestSupportedGradleVersion, "--profile", "heap-dump", "assemble")

        then:
        // 2 warm ups and 1 measured build
        assertHasFile("heap-3.hprof")
        assertHasFile("heap-3.jmap")
        assertDoesNotHaveFile("heap-4.hprof")
        assertDoesNotHaveFile("heap-4.jmap")
    }

    def "generates a heap dump when run with tooling API and warm daemon and multiple iterations"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", latestSupportedGradleVersion, "--profile", "heap-dump", "-iterations", "2", "assemble")

        then:
        // 2 warm ups and 2 measured builds
        assertHasFile("heap-3.hprof")
        assertHasFile("heap-3.jmap")
        assertHasFile("heap-4.hprof")
        assertHasFile("heap-4.jmap")
    }

    private void assertHasFile(String partialName) {
        assert mkFile(partialName).file
    }

    private void assertDoesNotHaveFile(String partialName) {
        assert !mkFile(partialName).exists()
    }

    private File mkFile(String partialName) {
        new File(outputDir, "${latestSupportedGradleVersion}-${partialName}")
    }
}
