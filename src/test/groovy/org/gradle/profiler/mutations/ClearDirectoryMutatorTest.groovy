package org.gradle.profiler.mutations

class ClearDirectoryMutatorTest extends AbstractMutatorTest {

    def "clears directory contents"() {
        def testDir = tmpDir.newFolder()
        def targetDir = file(testDir, "target-dir")
        writeTree(targetDir, [
            "file1.txt": "content1",
            "file2.txt": "content2",
            "sub-dir": ["file3.txt": "content3"]
        ])

        def spec = mockConfigSpec("""{
            clear-dir {
                target = "target-dir"
            }
        }""")
        _ * spec.projectDir >> testDir

        when:
        def mutator = new ClearDirectoryMutator.Configurator().configure("clear-dir", spec)
        mutator.beforeScenario(scenarioContext)

        then: "Directory should exist but be empty"
        tree(targetDir) == [:]
    }

    def "clears directory with keep list"() {
        def testDir = tmpDir.newFolder()
        def targetDir = file(testDir, "target-dir")
        writeTree(targetDir, [
            "file1.txt": "content1",
            "file2.txt": "content2",
            "keep-me.txt": "keep content",
            "sub-dir": ["file3.txt": "content3"],
            "keep-dir": ["file4.txt": "content4"]
        ])

        def spec = mockConfigSpec("""{
            clear-dir {
                target = "target-dir"
                keep = ["keep-me.txt", "keep-dir"]
            }
        }""")
        _ * spec.projectDir >> testDir

        when:
        def mutator = new ClearDirectoryMutator.Configurator().configure("clear-dir", spec)
        mutator.beforeScenario(scenarioContext)

        then: "Directory should exist with only kept files"
        tree(targetDir) == [
            "keep-me.txt": "keep content",
            "keep-dir": ["file4.txt": "content4"]
        ]
    }

    def "has no problem if target does not exist"() {
        def testDir = tmpDir.newFolder()
        def targetDir = file(testDir, "missing-dir")

        def spec = mockConfigSpec("""{
            clear-dir = {
                target = "missing-dir"
            }
        }""")
        _ * spec.projectDir >> testDir

        when:
        def mutator = new ClearDirectoryMutator.Configurator().configure("clear-dir", spec)
        mutator.beforeScenario(scenarioContext)

        then: "Directory should not exist"
        tree(targetDir) == null
    }

    def "fails if target is a file"() {
        def testDir = tmpDir.newFolder()
        def targetFile = file(testDir, "file.txt")
        targetFile << "content"

        def spec = mockConfigSpec("""{
            clear-dir = {
                target = "file.txt"
            }
        }""")
        _ * spec.projectDir >> testDir

        def mutator = new ClearDirectoryMutator.Configurator().configure("clear-dir", spec)

        when:
        mutator.beforeScenario(scenarioContext)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("not a directory")
    }

    def "clears with empty keep list"() {
        def testDir = tmpDir.newFolder()
        def targetDir = file(testDir, "target-dir")
        writeTree(targetDir, [
            "file1.txt": "content1",
            "file2.txt": "content2"
        ])

        def spec = mockConfigSpec("""{
            clear-dir {
                target = "target-dir"
                keep = []
            }
        }""")
        _ * spec.projectDir >> testDir

        when:
        def mutator = new ClearDirectoryMutator.Configurator().configure("clear-dir", spec)
        mutator.beforeScenario(scenarioContext)

        then: "Directory should exist but be empty"
        tree(targetDir) == [:]
    }

    def "clears multiple directories with different schedules"() {
        def testDir = tmpDir.newFolder()
        def dirScenario = file(testDir, "dir-scenario")
        def dirCleanup = file(testDir, "dir-cleanup")
        def dirBuild = file(testDir, "dir-build")
        writeTree(dirScenario, ["file1.txt": "content1"])
        writeTree(dirCleanup, ["file2.txt": "content2"])
        writeTree(dirBuild, ["file3.txt": "content3"])

        def spec = mockConfigSpec("""{
            clear-dir = [{
                target = "dir-scenario"
            }, {
                target = "dir-cleanup"
                schedule = CLEANUP
            }, {
                target = "dir-build"
                schedule = BUILD
            }]
        }""")
        _ * spec.projectDir >> testDir

        def mutator = new ClearDirectoryMutator.Configurator().configure("clear-dir", spec)

        when:
        mutator.beforeScenario(scenarioContext)

        then: "Only scenario directory should be cleared"
        tree(dirScenario) == [:]
        tree(dirCleanup) == ["file2.txt": "content2"]
        tree(dirBuild) == ["file3.txt": "content3"]

        when:
        mutator.beforeCleanup(buildContext)

        then: "Cleanup directory should be cleared"
        tree(dirCleanup) == [:]
        tree(dirBuild) == ["file3.txt": "content3"]

        when:
        mutator.beforeBuild(buildContext)

        then: "Build directory should be cleared"
        tree(dirBuild) == [:]
    }
}
