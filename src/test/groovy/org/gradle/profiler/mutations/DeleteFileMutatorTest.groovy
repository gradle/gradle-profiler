package org.gradle.profiler.mutations

class DeleteFileMutatorTest extends AbstractMutatorTest {

    def "deletes file"() {
        def testDir = tmpDir.newFolder()
        def expectedContents = "Copy file from source to target"
        def testFile = new File(testDir, "single-file.txt")
        testFile.text = expectedContents

        def spec = mockConfigSpec("""{
            delete-file {
                target = "single-file.txt"
            }
        }""")
        _ * spec.projectDir >> testDir

        when:
        def mutator = new DeleteFileMutator.Configurator().configure("delete-file", spec)
        mutator.beforeScenario(buildContext)

        then: "File should not exist"
        !testFile.exists()
    }

    def "deletes directory"() {
        def testDir = tmpDir.newFolder()
        def expectedContents = "Copy file from source to target"
        def targetDir = new File(testDir, "target-dir")
        targetDir.mkdirs()
        new File(targetDir, "another-file.txt").text = expectedContents
        new File(testDir, "file.txt").text = expectedContents

        def spec = mockConfigSpec("""{
            delete-file {
                target = "target-dir"
            }
        }""")
        _ * spec.projectDir >> testDir

        when:
        def mutator = new DeleteFileMutator.Configurator().configure("delete-file", spec)
        mutator.beforeScenario(buildContext)

        then: "Directory should not exist"
        !targetDir.exists()
    }
}
