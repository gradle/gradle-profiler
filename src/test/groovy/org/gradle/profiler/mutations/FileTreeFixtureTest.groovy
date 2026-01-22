package org.gradle.profiler.mutations

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class FileTreeFixtureTest extends Specification implements FileTreeFixture {

    @Rule
    TemporaryFolder tmpDir = new TemporaryFolder()

    def "file creates File instance"() {
        def parent = tmpDir.root
        def child = "test.txt"

        when:
        def result = file(parent, child)

        then:
        result == new File(parent, child)
    }

    def "mkdirs creates directory and returns it"() {
        def dir = file(tmpDir.root, "test")

        when:
        def result = mkdirs(dir)

        then:
        result == dir
        dir.exists()
        dir.isDirectory()
    }

    def "mkdirs creates nested directories"() {
        def dir = file(tmpDir.root, "a/b/c")

        when:
        def result = mkdirs(dir)

        then:
        result == dir
        dir.exists()
        dir.isDirectory()
    }

    def "tree returns null for non-existent directory"() {
        def dir = file(tmpDir.root, "missing")

        expect:
        tree(dir) == null
    }

    def "tree returns file content for file"() {
        def testFile = file(tmpDir.root, "test.txt")
        testFile << "content"

        expect:
        tree(testFile) == "content"
    }

    def "tree returns empty map for empty directory"() {
        def dir = mkdirs(file(tmpDir.root, "empty"))

        expect:
        tree(dir) == [:]
    }

    def "tree returns map with files"() {
        def dir = mkdirs(file(tmpDir.root, "test"))
        file(dir, "file1.txt") << "content1"
        file(dir, "file2.txt") << "content2"

        expect:
        tree(dir) == [
            "file1.txt": "content1",
            "file2.txt": "content2"
        ]
    }

    def "tree returns nested structure"() {
        def dir = mkdirs(file(tmpDir.root, "test"))
        file(dir, "file1.txt") << "content1"
        def subDir = mkdirs(file(dir, "sub"))
        file(subDir, "file2.txt") << "content2"
        def subSubDir = mkdirs(file(subDir, "subsub"))
        file(subSubDir, "file3.txt") << "content3"

        expect:
        tree(dir) == [
            "file1.txt": "content1",
            "sub": [
                "file2.txt": "content2",
                "subsub": [
                    "file3.txt": "content3"
                ]
            ]
        ]
    }

    def "writeTree creates simple files"() {
        def dir = file(tmpDir.root, "test")
        writeTree(dir, [
            "file1.txt": "content1",
            "file2.txt": "content2"
        ])

        expect:
        file(dir, "file1.txt").text == "content1"
        file(dir, "file2.txt").text == "content2"
    }

    def "writeTree creates nested structure"() {
        def dir = file(tmpDir.root, "test")
        writeTree(dir, [
            "file1.txt": "content1",
            "sub": [
                "file2.txt": "content2",
                "subsub": [
                    "file3.txt": "content3"
                ]
            ]
        ])

        expect:
        tree(dir) == [
            "file1.txt": "content1",
            "sub": [
                "file2.txt": "content2",
                "subsub": [
                    "file3.txt": "content3"
                ]
            ]
        ]
    }

    def "writeTree and tree are symmetric"() {
        def dir = file(tmpDir.root, "test")
        def structure = [
            "a.txt": "content a",
            "b.txt": "content b",
            "dir1": [
                "c.txt": "content c",
                "dir2": [
                    "d.txt": "content d",
                    "e.txt": "content e"
                ]
            ],
            "dir3": [
                "f.txt": "content f"
            ]
        ]

        when:
        writeTree(dir, structure)

        then:
        tree(dir) == structure
    }
}
