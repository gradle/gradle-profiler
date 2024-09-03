package org.gradle.profiler.mutations

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import org.gradle.profiler.DefaultScenarioContext
import org.gradle.profiler.Phase
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

abstract class AbstractMutatorTest extends Specification {
    @Rule
    TemporaryFolder tmpDir = new TemporaryFolder()
    def scenarioContext = new DefaultScenarioContext(UUID.fromString("276d92f3-16ac-4064-9a18-5f1dfd67992f"), "testScenario")
    def buildContext = scenarioContext.withBuild(Phase.MEASURE, 7)

    protected static Config parseConfig(String config) {
        ConfigFactory.parseString(config, ConfigParseOptions.defaults().setAllowMissing(false))
            .resolve()
    }

    protected BuildMutatorConfigurator.BuildMutatorConfiguratorSpec mockConfigSpec(String config) {
        Mock(BuildMutatorConfigurator.BuildMutatorConfiguratorSpec) {
            scenario >> parseConfig(config)
        }
    }
}
