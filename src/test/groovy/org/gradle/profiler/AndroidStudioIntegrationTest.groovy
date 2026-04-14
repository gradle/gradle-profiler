package org.gradle.profiler


import org.gradle.profiler.spock.extensions.ShowIdeLogsOnFailure
import org.gradle.profiler.studio.AndroidStudioTestSupport
import org.gradle.profiler.studio.IdeType
import org.gradle.profiler.studio.tools.AndroidStudioFinder
import spock.lang.Requires

import static org.gradle.profiler.studio.AndroidStudioTestSupport.setupLocalProperties

/**
 * You need ANDROID_HOME or ANDROID_SDK_ROOT set or
 * Android sdk installed in <user.home>/Library/Android/sdk (e.g. on Mac /Users/<username>/Library/Android/sdk)
 */
@ShowIdeLogsOnFailure
@Requires({ AndroidStudioFinder.findStudioHome() })
@Requires({ AndroidStudioTestSupport.findAndroidSdkPath() })
class AndroidStudioIntegrationTest extends AbstractIdeSyncIntegrationTest {

    @Override
    File findIdeHome() {
        return AndroidStudioFinder.findStudioHome()
    }

    @Override
    IdeType ideType() {
        return IdeType.ANDROID_STUDIO
    }

    @Override
    void setupProject() {
        setupLocalProperties(file("local.properties"))
    }

    def "fails when idea-sync points to Android Studio install dir"() {
        given:
        def scenarioFile = file("performance.scenarios") << """
            $scenarioName {
                idea-sync {}
            }
        """

        when:
        runBenchmark(scenarioFile, 1, 1, "--idea-install-dir", ideHome.absolutePath)

        then:
        def e = thrown(Main.ScenarioFailedException)
        e.cause.message.startsWith("Expected IntelliJ IDEA installation at ${ideHome.absolutePath}")
        e.cause.message.contains("but no starter executable found at any of: ")
    }
}
