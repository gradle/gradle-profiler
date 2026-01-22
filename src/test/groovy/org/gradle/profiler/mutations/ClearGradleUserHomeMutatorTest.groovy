package org.gradle.profiler.mutations

class ClearGradleUserHomeMutatorTest extends AbstractMutatorTest {

    def "clears Gradle user home but preserves wrapper directory"() {
        def testDir = tmpDir.newFolder()
        def gradleUserHome = file(testDir, "gradle-home")
        writeTree(gradleUserHome, [
            "caches" : ["file1.txt": "content1"],
            "daemon" : ["file2.txt": "content2"],
            "wrapper": [
                "dists": [
                    "gradle-8.0": ["file3.txt": "distribution"]
                ]
            ],
        ])

        def spec = mockConfigSpec("""{
            clear-gradle-user-home-before = SCENARIO
        }""")
        _ * spec.projectDir >> testDir
        _ * spec.gradleUserHome >> gradleUserHome

        when:
        def mutator = new ClearGradleUserHomeMutator.Configurator().configure("clear-gradle-user-home-before", spec)
        mutator.beforeScenario(scenarioContext)

        then: "Wrapper directory should be preserved"
        tree(gradleUserHome) == [
            "wrapper": [
                "dists": [
                    "gradle-8.0": ["file3.txt": "distribution"]
                ]
            ]
        ]
    }
}
