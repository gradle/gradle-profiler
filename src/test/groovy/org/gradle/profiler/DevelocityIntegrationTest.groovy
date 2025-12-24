package org.gradle.profiler

import org.gradle.profiler.buildscan.BuildScanProfiler
import org.gradle.profiler.fixtures.AbstractProfilerIntegrationTest
import org.gradle.profiler.fixtures.compatibility.gradle.GradleVersionCompatibility
import org.gradle.util.GradleVersion

class DevelocityIntegrationTest extends AbstractProfilerIntegrationTest {

    def "profiles build using Build Scans, specified Gradle version and tasks"() {
        given:
        instrumentedBuildScript()

        when:
        run([
            "--gradle-version", minimalSupportedGradleVersion,
            "--profile", "buildscan",
            "assemble"
        ])

        then:
        checkInstrumentedBuildScriptOutputs(minimalSupportedGradleVersion, "assemble")
        assertBuildScanPublished(BuildScanProfiler.defaultBuildScanVersion(GradleVersion.version(minimalSupportedGradleVersion)))
    }

    def "profiles build using Build Scans with latest supported Gradle version"() {
        given:
        instrumentedBuildScript()

        when:
        run([
            "--gradle-version", latestSupportedGradleVersion,
            "--profile", "buildscan",
            "assemble"
        ])

        then:
        checkInstrumentedBuildScriptOutputs(minimalSupportedGradleVersion, "assemble")
        assertBuildScanPublished(BuildScanProfiler.defaultBuildScanVersion(GradleVersion.version(latestSupportedGradleVersion)))
    }

    def "uses build scan version used by the build if present"() {
        def develocityVersion = develocityPluginVersion(minimalSupportedGradleVersion)
        given:
        settingsFile.text = """
            plugins {
                id "com.gradle.develocity" version "${develocityVersion}"
            }
            develocity.buildScan.termsOfUseUrl = "https://gradle.com/help/legal-terms-of-use"
            develocity.buildScan.termsOfUseAgree = "yes"
        """

        instrumentedBuildScript()

        when:
        run([
            "--gradle-version", minimalSupportedGradleVersion,
            "--profile", "buildscan",
            "assemble"
        ])

        then:
        checkInstrumentedBuildScriptOutputs(minimalSupportedGradleVersion, "assemble")
        assertBuildScanPublished(null, 2)
    }

    def "uses #pluginName plugin version used by the build if present"() {
        def isEnterprisePlugin = pluginName == 'Enterprise'
        def useOrService = "termsOf${isEnterprisePlugin ? 'Service' : 'Use'}"

        given:
        settingsFile.text = """
            plugins {
                id "$pluginId" version "$pluginVersion"
            }

            plugins.withId('$pluginId') {
                println("$pluginName plugin loaded from: " + it.class.protectionDomain.codeSource.location.path)
            }

            $extensionName {
                buildScan {
                    ${useOrService}Url = 'https://gradle.com/terms-of-service';
                    ${useOrService}Agree = 'yes'
                }
            }
        """

        instrumentedBuildScript()

        when:
        run([
            "--gradle-version", latestSupportedGradleVersion,
            "--profile", "buildscan",
            "assemble"
        ])

        then:
        checkInstrumentedBuildScriptOutputs(minimalSupportedGradleVersion, "assemble")

        // The plugin is applied in the build so it's used in probe run too
        logFile.find(~/$pluginName plugin loaded from:.*$jarFileName-${pluginVersion}.jar.*/).size() == totalProbeAndRunCount

        // The Develocity plugin publishes by default, so the probing build will also publish
        assertBuildScanPublished(null, isEnterprisePlugin ? 1 : 2)

        where:
        pluginName   | pluginId                | extensionName      | jarFileName                       | pluginVersion
        'Enterprise' | 'com.gradle.enterprise' | 'gradleEnterprise' | 'gradle-enterprise-gradle-plugin' | '3.13.1'
        'Develocity' | 'com.gradle.develocity' | 'develocity'       | 'develocity-gradle-plugin'        | '4.2.2'
    }

    def "profiles build using Build Scans overridden version specified in Gradle #gradleVersion and tasks"() {
        given:
        instrumentedBuildScript()

        def requestedBuildScanVersion
        if (GradleVersion.version(gradleVersion) < GradleVersion.version("5.0")) {
            requestedBuildScanVersion = "1.2"
        } else if (GradleVersion.version(gradleVersion) < GradleVersion.version("6.0")) {
            requestedBuildScanVersion = "2.2.1"
        } else {
            requestedBuildScanVersion = "4.2.2"
        }

        when:
        run([
            "--gradle-version", gradleVersion,
            "--profile", "buildscan", "--buildscan-version", requestedBuildScanVersion,
            "assemble"
        ])

        then:
        checkInstrumentedBuildScriptOutputs(minimalSupportedGradleVersion, "assemble")
        assertBuildScanPublished(requestedBuildScanVersion)

        where:
        gradleVersion << [minimalSupportedGradleVersion, latestSupportedGradleVersion]
    }

    def "profiles build using requested Develocity plugin version"() {
        given:
        settingsFile << """
            plugins.withId('com.gradle.develocity') {
                println("Develocity plugin loaded from: " + it.class.protectionDomain.codeSource.location.path)
            }
        """
        instrumentedBuildScript()

        when:
        run([
            "--gradle-version", latestSupportedGradleVersion,
            "--profile", "buildscan", "--buildscan-version", pluginVersion,
            "assemble"
        ])

        then:
        checkInstrumentedBuildScriptOutputs(latestSupportedGradleVersion, "assemble")

        logFile.find(~/Develocity plugin loaded from:.*develocity-gradle-plugin-${pluginVersion}.jar.*/).size() == totalRunCount
        assertBuildScanPublished(pluginVersion)

        where:
        pluginVersion << ["4.2.2"]
    }

    def "profiles build using JFR, Build Scans, specified Gradle version and tasks"() {
        def gradleVersion = minimalSupportedGradleVersion
        def buildScanVersion = BuildScanProfiler.defaultBuildScanVersion(GradleVersion.version(gradleVersion))
        given:
        instrumentedBuildScript()

        when:
        run([
            "--gradle-version", gradleVersion,
            "--profile", "buildscan", "--buildscan-version", buildScanVersion,
            "--profile", "jfr",
            "assemble"
        ])

        then:
        checkInstrumentedBuildScriptOutputs(gradleVersion, "assemble")
        assertBuildScanPublished(buildScanVersion)

        def profileFile = new File(outputDir, "${gradleVersion}.jfr")
        profileFile.isFile()
    }

    void assertBuildScanPublished(String buildScanPluginVersion = null, int numberOfScans = 1) {
        if (buildScanPluginVersion) {
            assert logFile.find("Using build scan plugin " + buildScanPluginVersion).size() == 1
        } else {
            assert logFile.find("Using build scan plugin specified in the build").size() == 1
        }
        assert logFile.find(~/Publishing [bB]uild .*/).size() == numberOfScans: ("LOG FILE:" + logFile.text)
    }

    // https://docs.gradle.com/develocity/current/miscellaneous/compatibility/#build-scans
    static String develocityPluginVersion(String gradleVersion) {
        GradleVersionCompatibility.checkSupported(gradleVersion)
        "4.2.2"
    }
}
