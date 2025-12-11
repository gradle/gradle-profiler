package org.gradle.profiler.fixtures.compatibility.gradle


import org.gradle.profiler.fixtures.AbstractBaseProfilerIntegrationTest
import org.gradle.util.GradleVersion

@GradleCrossVersionTest
abstract class AbstractGradleCrossVersionTest extends AbstractBaseProfilerIntegrationTest {

    // Context set by cross-version test infrastructure, see GradleCrossVersionTestInterceptor
    public GradleVersion primaryGradleVersion

    String getGradleVersion() {
        return primaryGradleVersion.version
    }

    def setup() {
        downgradeDaemonJvmIfTestJvmUnsupported(gradleVersion)
    }

    def gradleVersionWithExperimentalConfigurationCache() {
        primaryGradleVersion >= GradleVersionCompatibility.minimalGradleVersionWithExperimentalConfigurationCache
    }
}
