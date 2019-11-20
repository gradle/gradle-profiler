package org.gradle.profiler.mutations

class ApplyChangeToNativeSourceFileMutatorTest extends AbstractMutatorTest {

    def "adds and replaces method to end of cpp source file"() {
        def sourceFile = tmpDir.newFile("Thing.cpp")
        sourceFile.text = " "
        def mutator = new ApplyChangeToNativeSourceFileMutator(sourceFile)

        when:
        mutator.beforeBuild(buildContext)

        then:
        sourceFile.text == " \nint _mUNIQUE_ID () { }"
        1 * buildContext.uniqueBuildId >> "UNIQUE_ID"
        0 * _
    }

    def "adds and replaces method to end of h source file"() {
        def sourceFile = tmpDir.newFile("Thing.h")
        sourceFile.text = "#ifndef ABC\n\n#endif"

        def mutator = new ApplyChangeToNativeSourceFileMutator(sourceFile)

        when:
        mutator.beforeBuild(buildContext)

        then:
        sourceFile.text == "#ifndef ABC\n\nint _mUNIQUE_ID();\n#endif"
        1 * buildContext.uniqueBuildId >> "UNIQUE_ID"
        0 * _
    }

    def "uses same name for method in CPP and H files"() {
        def sourceFileCPP = tmpDir.newFile("Thing.cpp")
        def sourceFileH = tmpDir.newFile("Thing.h")
        sourceFileCPP.text = " "
        sourceFileH.text = "#ifndef ABC\n\n#endif"

        def mutatorCPP = new ApplyChangeToNativeSourceFileMutator(sourceFileCPP)
        def mutatorH = new ApplyChangeToNativeSourceFileMutator(sourceFileH)

        when:
        mutatorCPP.beforeBuild(buildContext)

        then:
        sourceFileCPP.text == " \nint _mUNIQUE_ID () { }"
        1 * buildContext.uniqueBuildId >> "UNIQUE_ID"

        when:
        mutatorH.beforeBuild(buildContext)

        then:
        sourceFileH.text == "#ifndef ABC\n\nint _mUNIQUE_ID();\n#endif"
        1 * buildContext.uniqueBuildId >> "UNIQUE_ID"
        0 * _
    }

}
