package org.gradle.profiler.fixtures.compatibility.gradle

import org.gradle.api.JavaVersion
import org.gradle.profiler.fixtures.AbstractBaseProfilerIntegrationTest
import org.gradle.util.GradleVersion

import javax.annotation.Nullable

@GradleCrossVersionTest
abstract class AbstractGradleCrossVersionTest extends AbstractBaseProfilerIntegrationTest {

    // Context set by cross-version test infrastructure, see GradleCrossVersionTestInterceptor
    public static GradleVersion primaryGradleVersion

    String getGradleVersion() {
        return primaryGradleVersion.version
    }

    def setup() {
        downgradeDaemonJvmIfTestJvmUnsupported(gradleVersion)
    }
}
