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
        mutator.beforeScenario(scenarioContext)

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
            delete-file = {
                target = "target-dir"
            }
        }""")
        _ * spec.projectDir >> testDir

        when:
        def mutator = new DeleteFileMutator.Configurator().configure("delete-file", spec)
        mutator.beforeScenario(scenarioContext)

        then: "Directory should not exist"
        !targetDir.exists()
    }

    def "has no problem if target does not exist"() {
        def testDir = tmpDir.newFolder()
        def testFile = new File(testDir, "missing-file.txt")

        def spec = mockConfigSpec("""{
            delete-file = {
                target = "missing-file.txt"
            }
        }""")
        _ * spec.projectDir >> testDir

        when:
        def mutator = new DeleteFileMutator.Configurator().configure("delete-file", spec)
        mutator.beforeScenario(scenarioContext)

        then: "File should not exist"
        !testFile.exists()
    }

    def "deletes multiple files"() {
        def testDir = tmpDir.newFolder()
        def expectedContents = "Copy file from source to target"
        def fileScenario = new File(testDir, "file-scenario.txt")
        fileScenario.text = expectedContents
        def fileCleanup = new File(testDir, "file-cleanup.txt")
        fileCleanup.text = expectedContents
        def fileBuild = new File(testDir, "file-build.txt")
        fileBuild.text = expectedContents

        def spec = mockConfigSpec("""{
            delete-file = [{
                target = "file-scenario.txt"
            }, {
                target = "file-cleanup.txt"
                schedule = CLEANUP
            }, {
                target = "file-build.txt"
                schedule = BUILD
            }]
        }""")
        _ * spec.projectDir >> testDir

        def mutator = new DeleteFileMutator.Configurator().configure("delete-file", spec)

        when:
        mutator.beforeScenario(scenarioContext)

        then: "Files should not exist"
        !fileScenario.exists()
        fileCleanup.exists()
        fileBuild.exists()

        when:
        mutator.beforeCleanup(buildContext)

        then:
        !fileCleanup.exists()
        fileBuild.exists()

        when:
        mutator.beforeBuild(buildContext)

        then:
        !fileBuild.exists()
    }

    def "deletes file with GRADLE_USER_HOME root"() {
        def projectDir = tmpDir.newFolder()
        def gradleUserHome = tmpDir.newFolder()

        def targetFile = file(gradleUserHome, "caches/file.bin")
        writeTree(targetFile.parentFile, [
            "file.bin": "content"
        ])

        def spec = mockConfigSpec("""{
            delete-file {
                root = GRADLE_USER_HOME
                target = "caches/file.bin"
            }
        }""")
        _ * spec.projectDir >> projectDir
        _ * spec.gradleUserHome >> gradleUserHome

        when:
        def mutator = new DeleteFileMutator.Configurator().configure("delete-file", spec)
        mutator.beforeScenario(scenarioContext)

        then:
        !targetFile.exists()
    }

    def "absolute path ignores root parameter in delete"() {
        def absoluteDir = tmpDir.newFolder()
        def targetFile = file(absoluteDir, "file.txt")
        writeTree(absoluteDir, [
            "file.txt": "content"
        ])

        def spec = mockConfigSpec("""{
            delete-file {
                root = GRADLE_USER_HOME
                target = "${normaliseFileSeparators(targetFile.absolutePath)}"
            }
        }""")

        when:
        def mutator = new DeleteFileMutator.Configurator().configure("delete-file", spec)
        mutator.beforeScenario(scenarioContext)

        then:
        !targetFile.exists()
    }
}
