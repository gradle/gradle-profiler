package org.gradle.profiler

import org.gradle.profiler.fixtures.AbstractIdeIntegrationTest
import org.gradle.profiler.ide.tools.IdeFinder
import spock.lang.Requires

/**
 * Integration tests for IDE sync benchmarking using IntelliJ IDEA.
 * Requires an IntelliJ IDEA installation (set INTELLIJ_HOME or install to the default Applications directory).
 *
 * @see AndroidStudioIntegrationTest for the Android Studio variant (requires Android SDK)
 */
@Requires({ IdeFinder.findIntelliJHome() })
class IntelliJIntegrationTest extends AbstractIdeIntegrationTest {

    def setup() {
        ideHome = IdeFinder.findIntelliJHome()
    }
}
