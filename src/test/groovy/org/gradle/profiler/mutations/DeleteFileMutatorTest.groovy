package org.gradle.profiler.mutations

class DeleteFileMutatorTest extends AbstractMutatorTest {

    def "Delete file"() {
        def testDir = tmpDir.newFolder()
        def expectedContents = "Copy file from source to target"
        def testFile = new File(testDir, "single-file.txt")
        testFile.text = expectedContents

        when:
        def mutator = new DeleteFileMutator(testFile)
        mutator.beforeScenario(buildContext)

        then: "File should not exist"
        !testFile.exists()
    }

    def "Delete Directory"() {
        def testDir = new File(tmpDir.newFolder(), "delete-test-dir")
        def expectedContents = "Copy file from source to target"
        def nestedDir = new File(testDir, "nested")
        nestedDir.mkdirs()
        new File(nestedDir, "another-file.txt").text = expectedContents
        new File(testDir, "file.txt").text = expectedContents

        when:
        def mutator = new DeleteFileMutator(testDir)
        mutator.beforeScenario(buildContext)

        then: "Directory should not exist"
        !testDir.exists()
    }
}
