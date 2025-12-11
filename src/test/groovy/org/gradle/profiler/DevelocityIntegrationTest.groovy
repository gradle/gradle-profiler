package org.gradle.profiler

import org.gradle.profiler.buildscan.BuildScanProfiler
import org.gradle.profiler.fixtures.AbstractProfilerIntegrationTest
import org.gradle.profiler.fixtures.compatibility.gradle.GradleVersionCompatibility
import org.gradle.util.GradleVersion

class DevelocityIntegrationTest extends AbstractProfilerIntegrationTest {

    def "profiles build using Build Scans, specified Gradle version and tasks"() {
        given:
        buildFile.text = """
            apply plugin: BasePlugin
            println "<gradle-version: " + gradle.gradleVersion + ">"
            println "<tasks: " + gradle.startParameter.taskNames + ">"
            println "<daemon: " + gradle.services.get(org.gradle.internal.environment.GradleBuildEnvironment).longLivingProcess + ">"
        """

        when:
        run([
            "--gradle-version", minimalSupportedGradleVersion,
            "--profile", "buildscan",
            "assemble"
        ])

        then:
        // Probe version, 2 warm up, 1 build
        logFile.find("<gradle-version: $minimalSupportedGradleVersion>").size() == 4
        logFile.find("<daemon: true").size() == 4
        logFile.find("<tasks: [assemble]>").size() == 3
        assertBuildScanPublished(BuildScanProfiler.defaultBuildScanVersion(GradleVersion.version(minimalSupportedGradleVersion)))
    }

    def "profiles build using Build Scans with latest supported Gradle version"() {
        given:
        buildFile.text = """
            apply plugin: BasePlugin
            println "<gradle-version: " + gradle.gradleVersion + ">"
        """

        when:
        run([
            "--gradle-version", latestSupportedGradleVersion,
            "--profile", "buildscan",
            "assemble"
        ])

        then:
        logFile.find("<gradle-version: $latestSupportedGradleVersion>").size() == 4
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
        buildFile.text = """
            apply plugin: BasePlugin

            println "<gradle-version: " + gradle.gradleVersion + ">"
            println "<tasks: " + gradle.startParameter.taskNames + ">"
            println "<daemon: " + gradle.services.get(org.gradle.internal.environment.GradleBuildEnvironment).longLivingProcess + ">"
        """

        when:
        run([
            "--gradle-version", minimalSupportedGradleVersion,
            "--profile", "buildscan",
            "assemble"
        ])

        then:
        // Probe version, 2 warm up, 1 build
        logFile.find("<gradle-version: $minimalSupportedGradleVersion>").size() == 4
        logFile.find("<daemon: true").size() == 4
        logFile.find("<tasks: [assemble]>").size() == 3
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
        buildFile.text = """
            apply plugin: BasePlugin
            println "<gradle-version: " + gradle.gradleVersion + ">"
            println "<tasks: " + gradle.startParameter.taskNames + ">"
            println "<daemon: " + gradle.services.get(org.gradle.internal.environment.GradleBuildEnvironment).longLivingProcess + ">"
        """

        when:
        run([
            "--gradle-version", latestSupportedGradleVersion,
            "--profile", "buildscan",
            "assemble"
        ])

        then:
        // Probe version, 2 warm up, 1 build
        logFile.find("<gradle-version: $latestSupportedGradleVersion>").size() == 4
        logFile.find("<daemon: true").size() == 4
        logFile.find("<tasks: [assemble]>").size() == 3
        // Enterprise plugin is applied in the build so it's used in probe version build, 2 warm ups and 1 build == 4
        logFile.find(~/$pluginName plugin loaded from:.*$jarFileName-${pluginVersion}.jar.*/).size() == 4
        // The Develocity plugin publishes by default, so the probing build will also publish
        assertBuildScanPublished(null, isEnterprisePlugin ? 1 : 2)

        where:
        pluginName   | pluginId                | extensionName      | jarFileName                       | pluginVersion
        'Enterprise' | 'com.gradle.enterprise' | 'gradleEnterprise' | 'gradle-enterprise-gradle-plugin' | '3.0'
        'Develocity' | 'com.gradle.develocity' | 'develocity'       | 'develocity-gradle-plugin'        | '4.2.2'
    }

    def "profiles build using Build Scans overridden version specified in Gradle #gradleVersion and tasks"() {
        given:
        buildFile.text = """
            apply plugin: BasePlugin
            println "<gradle-version: " + gradle.gradleVersion + ">"
            println "<tasks: " + gradle.startParameter.taskNames + ">"
            println "<daemon: " + gradle.services.get(org.gradle.internal.environment.GradleBuildEnvironment).longLivingProcess + ">"
        """
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
        // Probe version, 2 warm up, 1 build
        logFile.find("<gradle-version: $gradleVersion>").size() == 4
        logFile.find("<daemon: true").size() == 4
        logFile.find("<tasks: [assemble]>").size() == 3
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
        buildFile.text = """
            apply plugin: BasePlugin
            println "<gradle-version: " + gradle.gradleVersion + ">"
            println "<tasks: " + gradle.startParameter.taskNames + ">"
            println "<daemon: " + gradle.services.get(org.gradle.internal.environment.GradleBuildEnvironment).longLivingProcess + ">"
        """
        def requestedGradleEnterpriseVersion = "4.2.2"

        when:
        run([
            "--gradle-version", latestSupportedGradleVersion,
            "--profile", "buildscan", "--buildscan-version", requestedGradleEnterpriseVersion,
            "assemble"
        ])

        then:
        // Probe version, 2 warm up, 1 build
        logFile.find("<gradle-version: $latestSupportedGradleVersion>").size() == 4
        logFile.find("<daemon: true").size() == 4
        logFile.find("<tasks: [assemble]>").size() == 3
        // Develocity plugin is not applied to probe version build, but just to 2 warm ups and 1 build == 3
        logFile.find(~/Develocity plugin loaded from:.*develocity-gradle-plugin-4.2.2.jar.*/).size() == 3
        assertBuildScanPublished(requestedGradleEnterpriseVersion)
    }

    def "profiles build using JFR, Build Scans, specified Gradle version and tasks"() {
        def gradleVersion = minimalSupportedGradleVersion
        def buildScanVersion = BuildScanProfiler.defaultBuildScanVersion(GradleVersion.version(gradleVersion))
        given:
        buildFile.text = """
            apply plugin: BasePlugin
            println "<gradle-version: " + gradle.gradleVersion + ">"
            println "<tasks: " + gradle.startParameter.taskNames + ">"
            println "<daemon: " + gradle.services.get(org.gradle.internal.environment.GradleBuildEnvironment).longLivingProcess + ">"
        """

        when:
        run([
            "--gradle-version", gradleVersion,
            "--profile", "buildscan", "--buildscan-version", buildScanVersion,
            "--profile", "jfr",
            "assemble"
        ])

        then:
        // Probe version, 2 warm up, 1 build
        logFile.find("<gradle-version: ${gradleVersion}>").size() == 4
        logFile.find("<daemon: true").size() == 4
        logFile.find("<tasks: [assemble]>").size() == 3
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
