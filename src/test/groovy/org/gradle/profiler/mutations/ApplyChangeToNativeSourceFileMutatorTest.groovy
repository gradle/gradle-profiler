package org.gradle.profiler.mutations

import spock.lang.Unroll

@Unroll
class ApplyChangeToNativeSourceFileMutatorTest extends AbstractMutatorTest {

    def "adds and replaces method to end of #extension source file"() {
        def sourceFile = tmpDir.newFile("Thing.${extension}")
        sourceFile.text = " "
        def mutator = new ApplyChangeToNativeSourceFileMutator(sourceFile)

        when:
        mutator.beforeScenario(scenarioContext)
        mutator.beforeBuild(buildContext)

        then:
        sourceFile.text == " \nint _m_276d92f3_16ac_4064_9a18_5f1dfd67992f_testScenario_3c4925d7_MEASURE_7 () { }"

        where:
        extension << ["c", "C", "cpp", "CPP", "c++", "cxx"]
    }

    def "adds and replaces method to end of h source file"() {
        def sourceFile = tmpDir.newFile("Thing.h")
        sourceFile.text = "#ifndef ABC\n\n#endif"

        def mutator = new ApplyChangeToNativeSourceFileMutator(sourceFile)

        when:
        mutator.beforeScenario(scenarioContext)
        mutator.beforeBuild(buildContext)

        then:
        sourceFile.text == "#ifndef ABC\n\nint _m_276d92f3_16ac_4064_9a18_5f1dfd67992f_testScenario_3c4925d7_MEASURE_7();\n#endif"

        where:
        extension << ["h", "H", "hpp", "HPP", "hxx"]
    }

    def "uses same name for method in CPP and H files"() {
        def sourceFileCPP = tmpDir.newFile("Thing.cpp")
        def sourceFileH = tmpDir.newFile("Thing.h")
        sourceFileCPP.text = " "
        sourceFileH.text = "#ifndef ABC\n\n#endif"

        def mutatorC = new ApplyChangeToNativeSourceFileMutator(sourceFileCPP)
        def mutatorH = new ApplyChangeToNativeSourceFileMutator(sourceFileH)

        when:
        mutatorC.beforeScenario(scenarioContext)
        mutatorC.beforeBuild(buildContext)

        then:
        sourceFileCPP.text == " \nint _m_276d92f3_16ac_4064_9a18_5f1dfd67992f_testScenario_3c4925d7_MEASURE_7 () { }"

        when:
        mutatorH.beforeScenario(scenarioContext)
        mutatorH.beforeBuild(buildContext)

        then:
        sourceFileH.text == "#ifndef ABC\n\nint _m_276d92f3_16ac_4064_9a18_5f1dfd67992f_testScenario_3c4925d7_MEASURE_7();\n#endif"
    }

    def "complains when a file with not-allowed file extension is specified"() {
        def file = tmpDir.newFile("Thing.png")

        when:
        new ApplyChangeToNativeSourceFileMutator(file)

        then:
        thrown(IllegalArgumentException)
    }
}
