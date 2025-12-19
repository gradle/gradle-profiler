package org.gradle.profiler.fixtures.compatibility.gradle


import org.gradle.util.GradleVersion
import spock.util.environment.Jvm

class GradleVersionCompatibility {

    // See: https://github.com/gradle/gradle/blob/bc997dc1751c50fa0cdb1aa1fefe4844b81eca9f/testing/internal-integ-testing/src/main/groovy/org/gradle/integtests/fixtures/executer/DefaultGradleDistribution.groovy#L32-L50
    static GradleVersion minimalGradleVersionSupportingJava11 = GradleVersion.version("5.0")
    static GradleVersion minimalGradleVersionSupportingJava17 = GradleVersion.version("7.3")
    static GradleVersion lastGradleVersionSupportingJava11 = GradleVersion.version("8.14.3")

    static GradleVersion minimalGradleVersionWithExperimentalConfigurationCache = GradleVersion.version("6.6")

    // advanced means support of --measure-build-op, --measure-config-time, --measure-gc
    static GradleVersion minimalGradleVersionWithAdvancedBenchmarking = GradleVersion.version("6.1")

    static GradleVersion minimalSupportedGradleVersion = GradleVersion.version("6.0")

    static List<GradleVersion> testedGradleVersions = gradleVersions(
        minimalSupportedGradleVersion.version,
        "6.9.4",
        "7.6.4",
        "8.0.2",
        "8.14.3",
        "9.1.0"
    )

    static List<GradleVersion> gradleVersionsSupportedOnCurrentJvm(List<GradleVersion> gradleVersions) {
        gradleVersions.findAll {
            new DefaultGradleDistribution(it).daemonWorksWith(Jvm.current.javaSpecVersionNumber.major)
        }
    }

    static String transformCacheLocation(String gradleVersionString) {
        def gradleVersion = GradleVersion.version(gradleVersionString)
        if (gradleVersion < GradleVersion.version("5.1")) {
            return 'transforms-1/files-1.1'
        }
        if (gradleVersion < GradleVersion.version('6.8')) {
            return 'transforms-2/files-2.1'
        }
        return "transforms-3"
    }

    static void checkSupported(String gradleVersion) {
        assert GradleVersion.version(gradleVersion) >= minimalSupportedGradleVersion
    }

    static void checkAllTestedVersionsSupportJava11() {
        def preJava11 = testedGradleVersions.findAll {
            it < minimalGradleVersionSupportingJava11
        }

        assert preJava11.empty
    }

    static def gradleVersions(String... versions) {
        versions.collect { GradleVersion.version(it) }
    }
}
