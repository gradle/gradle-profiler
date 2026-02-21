package org.gradle.profiler

import org.gradle.profiler.fixtures.AbstractIdeIntegrationTest
import org.gradle.profiler.studio.AndroidStudioTestSupport
import org.gradle.profiler.ide.tools.IdeFinder
import spock.lang.Requires

import static org.gradle.profiler.studio.AndroidStudioTestSupport.setupLocalProperties

/**
 * Integration tests for IDE sync benchmarking using Android Studio.
 * Requires an Android Studio installation and Android SDK (ANDROID_HOME or ANDROID_SDK_ROOT).
 *
 * @see IntelliJIntegrationTest for the IntelliJ IDEA variant
 */
@Requires({ IdeFinder.findAndroidStudioHome() })
@Requires({ AndroidStudioTestSupport.findAndroidSdkPath() })
class AndroidStudioIntegrationTest extends AbstractIdeIntegrationTest {

    def setup() {
        ideHome = IdeFinder.findAndroidStudioHome()
        setupLocalProperties(file("local.properties"))
    }

    def "doesn't write external annotations to .m2 folder"() {
        given:
        def scenarioFile = file("performance.scenarios") << """
            $scenarioName {
                ide-sync {
                }
            }
        """
        buildFile.text = """
            plugins {
                id 'java'
            }
            repositories {
                mavenCentral()
            }
            dependencies {
                // This dependency triggers download of external annotations
                // to <mavenRepo>/org/jetbrains/externalAnnotations
                implementation("com.fasterxml.jackson.core:jackson-core:2.9.6")
            }
        """
        def mavenRepository = tmpDir.createDir("maven/repository")
        new File("${sandboxDir.absolutePath}/config/options").mkdirs()
        new File("${sandboxDir.absolutePath}/config/options/path.macros.xml").text = """
<application>
  <component name="PathMacrosImpl">
    <macro name="MAVEN_REPOSITORY" value="${mavenRepository.absolutePath}" />
  </component>
</application>"""

        when:
        runBenchmark(scenarioFile, 1, 1)

        then:
        mavenRepository.list() == [] as String[]
    }
}
