package org.gradle.profiler


import org.gradle.profiler.studio.tools.IntellijFinder
import spock.lang.Requires

@Requires({ IntellijFinder.findIdeHome() })
class IntellijIntegrationTest extends AbstractIdeSyncIntegrationTest {

    @Override
    File findIdeHome() {
        return IntellijFinder.findIdeHome()
    }
}
