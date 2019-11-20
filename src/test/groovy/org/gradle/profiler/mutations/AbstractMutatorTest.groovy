package org.gradle.profiler.mutations

import org.gradle.profiler.BuildContext
import org.gradle.profiler.ScenarioContext
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

abstract class AbstractMutatorTest extends Specification {
    @Rule TemporaryFolder tmpDir = new TemporaryFolder()
    def scenarioContext = Mock(ScenarioContext)
    def buildContext = Mock(BuildContext)
}
