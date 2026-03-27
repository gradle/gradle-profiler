package org.gradle.profiler

import org.gradle.api.JavaVersion
import org.gradle.profiler.fixtures.compatibility.ide.IntellijGradleJvmCompatibility
import org.gradle.profiler.studio.tools.IntellijFinder
import spock.lang.Requires

@Requires({ IntellijFinder.findIdeHome() })
@Requires({ IntellijGradleJvmCompatibility.fromIdeHome().isSupported(JavaVersion.current()) })
class IntellijIntegrationTest extends AbstractIdeSyncIntegrationTest {

    @Override
    File findIdeHome() {
        return IntellijFinder.findIdeHome()
    }
}
