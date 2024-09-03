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
        mutator.beforeScenario(scenarioContext)

        then:
        target.exists()
        target.text == expectedContents
    }

    def "copies multiple sets of source and target"() {
        def testDir = tmpDir.newFolder()

        def expectedContents = "Copy file from source to target"
        def source = new File(testDir, "source.txt")
        source.text = expectedContents
        def targetScenario = new File(testDir, "nested/target-scenario.txt")
        def targetCleanup = new File(testDir, "nested/target-cleanup.txt")
        def targetBuild = new File(testDir, "nested/target-build.txt")

        def spec = mockConfigSpec("""{
            copy-file = [{
                source = "source.txt"
                target = "nested/target-scenario.txt"
            }, {
                source = "source.txt"
                target = "nested/target-cleanup.txt"
                schedule = CLEANUP
            }, {
                source = "source.txt"
                target = "nested/target-build.txt"
                schedule = BUILD
            }]
        }""")
        _ * spec.projectDir >> testDir

        def mutator = new CopyFileMutator.Configurator().configure("copy-file", spec)

        when:
        mutator.beforeScenario(scenarioContext)

        then:
        targetScenario.exists()
        targetScenario.text == expectedContents
        !targetCleanup.exists()
        !targetBuild.exists()

        when:
        mutator.beforeCleanup(buildContext)

        then:
        targetCleanup.exists()
        targetCleanup.text == expectedContents
        !targetBuild.exists()

        when:
        mutator.beforeBuild(buildContext)

        then:
        targetBuild.exists()
        targetBuild.text == expectedContents
    }
}
