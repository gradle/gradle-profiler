package org.gradle.profiler.mutations

import org.gradle.profiler.BuildContext
import org.gradle.profiler.DefaultScenarioContext
import org.gradle.profiler.Phase
import org.gradle.profiler.ScenarioContext
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

abstract class AbstractMutatorTest extends Specification {
    @Rule
    TemporaryFolder tmpDir = new TemporaryFolder()
    ScenarioContext scenarioContext
    BuildContext buildContext

    def setup() {
        def projectDir = tmpDir.getRoot()
        projectDir.mkdirs()
        scenarioContext = new DefaultScenarioContext(UUID.fromString("276d92f3-16ac-4064-9a18-5f1dfd67992f"), "testScenario", projectDir)
        buildContext = scenarioContext.withBuild(Phase.MEASURE, 7)
    }
}
