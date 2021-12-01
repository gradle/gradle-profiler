package org.gradle.profiler.mutations

class ApplyChangeToComposableKotlinFileMutatorTest extends AbstractMutatorTest {

    static ORIGINAL_COMPOSABLE_FILE_CONTENT = "import androidx.compose.material.Text\n" +
        "import androidx.compose.runtime.Composable\n\n" +
        "@Composable fun Greeting(name: String) {\n" +
        "   Text(text = \"Hello \$name!\")\n" +
        "}\n"

    static APPLIED_COMPOSABLE_CONTENT =
        "@androidx.compose.runtime.Composable fun M_276d92f3_16ac_4064_9a18_5f1dfd67992f_testScenario_3c4925d7_MEASURE_7() {}\n"

    def "adds public function at end of source file replacing its body"() {
        def sourceFile = tmpDir.newFile("Thing.kt")
        sourceFile.text = ORIGINAL_COMPOSABLE_FILE_CONTENT

        def mutator = new ApplyChangeToComposableKotlinFileMutator(sourceFile)

        when:
        mutator.beforeScenario(scenarioContext)
        mutator.beforeBuild(buildContext)

        then:
        sourceFile.text == ORIGINAL_COMPOSABLE_FILE_CONTENT + APPLIED_COMPOSABLE_CONTENT
    }

    def "reverts changes when nothing has been applied"() {
        def sourceFile = tmpDir.newFile("strings.xml")
        sourceFile.text = ORIGINAL_COMPOSABLE_FILE_CONTENT
        def mutator = new ApplyChangeToAndroidManifestFileMutator(sourceFile)

        when:
        mutator.beforeScenario(scenarioContext)
        mutator.afterScenario(scenarioContext)

        then:
        sourceFile.text == ORIGINAL_COMPOSABLE_FILE_CONTENT
    }

    def "reverts changes when changes has been applied"() {
        def sourceFile = tmpDir.newFile("Thing.kt")
        sourceFile.text = ORIGINAL_COMPOSABLE_FILE_CONTENT
        def mutator = new ApplyChangeToComposableKotlinFileMutator(sourceFile)

        when:
        mutator.beforeScenario(scenarioContext)
        sourceFile.text = "some-change"
        mutator.afterScenario(scenarioContext)

        then:
        sourceFile.text == ORIGINAL_COMPOSABLE_FILE_CONTENT
    }
}
