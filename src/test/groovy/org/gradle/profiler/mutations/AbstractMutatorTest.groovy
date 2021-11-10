package org.gradle.profiler.mutations

import org.gradle.profiler.DefaultScenarioContext
import org.gradle.profiler.Phase
import org.gradle.profiler.ScenarioDefinition
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

abstract class AbstractMutatorTest extends Specification {
    @Rule TemporaryFolder tmpDir = new TemporaryFolder()
    def scenarioDefinition = Mock(ScenarioDefinition) {
        getName() >> "testScenario"
    }
    def scenarioContext = new DefaultScenarioContext(UUID.fromString("276d92f3-16ac-4064-9a18-5f1dfd67992f"), scenarioDefinition)
    def buildContext = scenarioContext.withBuild(Phase.MEASURE, 7)
}
