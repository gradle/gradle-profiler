package org.gradle.profiler.mutations

class ApplyChangeToAndroidLayoutFileMutatorTest extends AbstractMutatorTest {

    def "adds new view at bottom of top level layout"() {
        def sourceFile = tmpDir.newFile("strings.xml")
        sourceFile.text = "<LinearLayout></LinearLayout>"
        def mutator = new ApplyChangeToAndroidLayoutFileMutator(sourceFile)

        when:
        mutator.beforeBuild(buildContext)

        then:
        sourceFile.text == """\
        <LinearLayout><View 
            android:id="@+id/view_276d92f3_16ac_4064_9a18_5f1dfd67992f_testScenario_3c4925d7_MEASURE_7"
            android:visibility="gone"
            android:layout_width="5dp"
            android:layout_height="5dp"/>

        </LinearLayout>""".stripIndent()
    }


    def "adds new view at bottom of top level db layout"() {
        def sourceFile = tmpDir.newFile("strings.xml")
        sourceFile.text = '<layout><LinearLayout></LinearLayout></layout>'
        def mutator = new ApplyChangeToAndroidLayoutFileMutator(sourceFile)

        when:
        mutator.beforeBuild(buildContext)

        then:
        sourceFile.text == """\
        <layout><LinearLayout><View 
            android:id="@+id/view_276d92f3_16ac_4064_9a18_5f1dfd67992f_testScenario_3c4925d7_MEASURE_7"
            android:visibility="gone"
            android:layout_width="5dp"
            android:layout_height="5dp"/>

        </LinearLayout></layout>""".stripIndent()
    }

    def "reverts changes when nothing has been applied"() {
        def sourceFile = tmpDir.newFile("strings.xml")
        sourceFile.text = "<LinearLayout></LinearLayout>"
        def mutator = new ApplyChangeToAndroidLayoutFileMutator(sourceFile)

        when:
        mutator.afterScenario(scenarioContext)

        then:
        sourceFile.text == "<LinearLayout></LinearLayout>"
    }

    def "reverts changes when changes have been applied"() {
        def sourceFile = tmpDir.newFile("strings.xml")
        sourceFile.text = "<LinearLayout></LinearLayout>"
        def mutator = new ApplyChangeToAndroidLayoutFileMutator(sourceFile)

        when:
        mutator.beforeBuild(buildContext)
        mutator.afterScenario(scenarioContext)

        then:
        sourceFile.text == "<LinearLayout></LinearLayout>"
    }
}
