package org.gradle.profiler.mutations

import org.gradle.profiler.BuildMutator
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class ApplyPatchProjectMutatorTest extends Specification {

    @Rule TemporaryFolder tmpDir = new TemporaryFolder()

    static final String ORIGINAL_SOURCE = """
        public class Main {
        \tpublic static void main(String... args) {
        \t\tSystem.out.println("Hello, World!");
        \t}
        }
    """.stripIndent()

    static final String PATCHED_SOURCE = """
        public class Main {
        \tpublic static void main(String... args) {
        \t\tSystem.out.println("Hello, World!");
        \t}
        
        \tpublic static int add(int a, int b) {
        \t\treturn a + b;
        \t}
        }
    """.stripIndent()

    BuildMutator mutator
    File projectDir
    File sourceFile
    File patchFile

    def setup() {
        projectDir = tmpDir.newFolder("project")
        sourceFile = new File(projectDir, "Main.java")
        sourceFile.text = ORIGINAL_SOURCE

        patchFile = tmpDir.newFile("add.patch")
        patchFile.text = """
            --- Main.java\t2017-03-30 22:00:42.000000000 +0200
            +++ Main2.java\t2017-03-30 22:00:45.000000000 +0200
            @@ -3,4 +3,8 @@
             \tpublic static void main(String... args) {
             \t\tSystem.out.println("Hello, World!");
             \t}
            +
            +\tpublic static int add(int a, int b) {
            +\t\treturn a + b;
            +\t}
             }
        """.stripIndent()

        mutator = new ApplyPatchProjectMutator(projectDir, patchFile)
    }

    def "applies patch"() {
        when:
        mutator.beforeBuild()

        then:
        sourceFile.text == PATCHED_SOURCE

        when:
        mutator.beforeBuild()

        then:
        sourceFile.text == PATCHED_SOURCE
    }

    def "reverts patch"() {
        when:
        mutator.beforeBuild()

        then:
        sourceFile.text == PATCHED_SOURCE

        when:
        mutator.cleanup()

        then:
        sourceFile.text == ORIGINAL_SOURCE
    }

    def "doesn't change source when not yet applied"() {
        def mutator = new ApplyPatchProjectMutator(projectDir, patchFile)

        when:
        mutator.cleanup()

        then:
        sourceFile.text == ORIGINAL_SOURCE
    }
}
