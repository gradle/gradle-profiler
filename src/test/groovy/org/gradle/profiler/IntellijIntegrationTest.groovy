package org.gradle.profiler


import org.gradle.profiler.studio.IdeType
import org.gradle.profiler.studio.tools.IntellijFinder
import spock.lang.Requires

@Requires({ IntellijFinder.findIdeHome() })
class IntellijIntegrationTest extends AbstractIdeSyncIntegrationTest {

    @Override
    File findIdeHome() {
        return IntellijFinder.findIdeHome()
    }

    @Override
    IdeType ideType() {
        return IdeType.INTELLIJ_IDEA
    }

    def "fails when android-studio-sync points to IntelliJ IDEA install dir"() {
        given:
        def scenarioFile = file("performance.scenarios") << """
            $scenarioName {
                android-studio-sync {}
            }
        """

        when:
        runBenchmark(scenarioFile, 1, 1, "--studio-install-dir", ideHome.absolutePath)

        then:
        def e = thrown(Main.ScenarioFailedException)
        e.cause.message.startsWith("Expected Android Studio installation at ${ideHome.absolutePath}")
        e.cause.message.contains("but no starter executable found at any of: ")
    }
}
