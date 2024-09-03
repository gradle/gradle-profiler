package org.gradle.profiler.mutations

class CopyFileMutatorTest extends AbstractMutatorTest {

    def "copies file from source to target"() {
        def testDir = tmpDir.newFolder()

        def expectedContents = "Copy file from source to target"
        def source = new File(testDir, "source.txt")
        source.text = expectedContents
        def target = new File(testDir, "nested/target.txt")

        def spec = mockConfigSpec("""{
            copy-file {
                source = "source.txt"
                target = "nested/target.txt"
            }
        }""")
        _ * spec.projectDir >> testDir

        when:
        def mutator = new CopyFileMutator.Configurator().configure("copy-file", spec)
        mutator.beforeScenario(buildContext)

        then:
        target.exists()
        target.text == expectedContents
    }
}