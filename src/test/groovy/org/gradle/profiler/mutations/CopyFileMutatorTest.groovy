package org.gradle.profiler.mutations

class CopyFileMutatorTest extends AbstractMutatorTest {

    def "copies file from source to target"() {
        def testDir = tmpDir.newFolder()

        def scenario = parseConfig("""{
            copy-file {
                source = "source.txt"
                target = "nested/target.txt"
            }
        }""")

        def spec = Mock(BuildMutatorConfigurator.BuildMutatorConfiguratorSpec)
        _ * spec.scenario >> scenario
        _ * spec.projectDir >> testDir

        def mutator = new CopyFileMutator.Configurator().configure("copy-file", spec)

        def expectedContents = "Copy file from source to target"
        def source = new File(testDir, "source.txt")
        source.text = expectedContents
        def target = new File(testDir, "nested/target.txt")

        when:
        mutator.beforeScenario(buildContext)

        then:
        target.exists()
        target.text == expectedContents
    }
}
