package org.gradle.profiler.mutations

class CopyFileMutatorTest extends AbstractMutatorTest {

    def "copies file from source to target"() {
        def testDir = tmpDir.newFolder()

        def expectedContents = "Copy file from source to target"
        def source = new File(testDir, "source.txt")
        source.text = expectedContents
        def target = new File(testDir, "nested/target.txt")

        def spec = mockConfigSpec("""{
            copy-file = {
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

    def "copies multiple sets of source and target"() {
        def testDir = tmpDir.newFolder()

        def expectedContents = "Copy file from source to target"
        def source = new File(testDir, "source.txt")
        source.text = expectedContents
        def target1 = new File(testDir, "nested/target1.txt")
        def target2 = new File(testDir, "nested/target2.txt")

        def spec = mockConfigSpec("""{
            copy-file = [{
                source = "source.txt"
                target = "nested/target1.txt"
            }, {
                source = "source.txt"
                target = "nested/target2.txt"
            }]
        }""")
        _ * spec.projectDir >> testDir

        when:
        def mutator = new CopyFileMutator.Configurator().configure("copy-file", spec)
        mutator.beforeScenario(buildContext)

        then:
        target1.exists()
        target1.text == expectedContents
        target2.exists()
        target2.text == expectedContents
    }
}
