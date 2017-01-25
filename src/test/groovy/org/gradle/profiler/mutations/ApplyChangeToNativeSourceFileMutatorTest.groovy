package org.gradle.profiler.mutations

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class ApplyChangeToNativeSourceFileMutatorTest extends Specification {
    @Rule TemporaryFolder tmpDir = new TemporaryFolder()

    def "adds and replaces method to end of cpp source file"() {
        def sourceFile = tmpDir.newFile("Thing.cpp")
        sourceFile.text = " "
        def mutator = new ApplyChangeToNativeSourceFileMutator(sourceFile)
        ApplyChangeToNativeSourceFileMutator.classCreationTime = 1234

        when:
        mutator.beforeBuild()

        then:
        sourceFile.text == " \nint _m_1234_1 () { }"

        when:
        mutator.beforeBuild()

        then:
        sourceFile.text == " \nint _m_1234_2 () { }"

        when:
        mutator.beforeBuild()

        then:
        sourceFile.text == " \nint _m_1234_3 () { }"
    }

    def "adds and replaces method to end of h source file"() {
        def sourceFile = tmpDir.newFile("Thing.h")
        sourceFile.text = "#ifndef ABC\n\n#endif"

        def mutator = new ApplyChangeToNativeSourceFileMutator(sourceFile)
        ApplyChangeToNativeSourceFileMutator.classCreationTime = 1234

        when:
        mutator.beforeBuild()

        then:
        sourceFile.text == "#ifndef ABC\n\nint _m_1234_1();\n#endif"

        when:
        mutator.beforeBuild()

        then:
        sourceFile.text == "#ifndef ABC\n\nint _m_1234_2();\n#endif"

        when:
        mutator.beforeBuild()

        then:
        sourceFile.text == "#ifndef ABC\n\nint _m_1234_3();\n#endif"
    }

    def "uses same name for method in CPP and H files"() {
        def sourceFileCPP = tmpDir.newFile("Thing.cpp")
        def sourceFileH = tmpDir.newFile("Thing.h")
        sourceFileCPP.text = " "
        sourceFileH.text = "#ifndef ABC\n\n#endif"

        def mutatorCPP = new ApplyChangeToNativeSourceFileMutator(sourceFileCPP)
        def mutatorH = new ApplyChangeToNativeSourceFileMutator(sourceFileH)

        ApplyChangeToNativeSourceFileMutator.classCreationTime = 1234

        when:
        mutatorCPP.beforeBuild()
        mutatorH.beforeBuild()

        then:
        sourceFileCPP.text == " \nint _m_1234_1 () { }"
        sourceFileH.text == "#ifndef ABC\n\nint _m_1234_1();\n#endif"

        when:
        mutatorCPP.beforeBuild()
        mutatorH.beforeBuild()

        then:
        sourceFileCPP.text == " \nint _m_1234_2 () { }"
        sourceFileH.text == "#ifndef ABC\n\nint _m_1234_2();\n#endif"
    }

}
