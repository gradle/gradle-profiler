package org.gradle.profiler


import org.gradle.profiler.spock.extensions.ShowIdeLogsOnFailure
import org.gradle.profiler.studio.AndroidStudioTestSupport
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
    void setupProject() {
        setupLocalProperties(file("local.properties"))
    }
}
