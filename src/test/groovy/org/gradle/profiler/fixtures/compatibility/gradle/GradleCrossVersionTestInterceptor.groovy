package org.gradle.profiler.fixtures.compatibility.gradle

import org.gradle.profiler.fixtures.compatibility.AbstractCrossVersionTestInterceptor
import org.gradle.profiler.fixtures.multitest.AbstractMultiTestInterceptor.Execution as MultiTestExecution
import org.gradle.util.GradleVersion
import org.spockframework.runtime.extension.IMethodInvocation

class GradleCrossVersionTestInterceptor extends AbstractCrossVersionTestInterceptor<GradleVersion> {

    protected GradleCrossVersionTestInterceptor(Class<?> target) {
        super(target, AbstractGradleCrossVersionTest, "Gradle")
    }

    @Override
    protected Collection<GradleVersion> getAllVersions() {
        GradleVersionCompatibility.testedGradleVersions
    }

    @Override
    protected void createExecutions() {
        // AbstractGradleCrossVersionTest simplifies JVM handling based on this assumption
        // See `downgradeDaemonJvmIfTestJvmUnsupported` method
        GradleVersionCompatibility.checkAllTestedVersionsSupportJava11()

        super.createExecutions()
    }

    @Override
    protected Collection<? extends Execution> createExecutionsFor(GradleVersion version) {
        return [new GradleVersionExecution(version)]
    }

    static class GradleVersionExecution extends MultiTestExecution {

        private final GradleVersion version;

        GradleVersionExecution(GradleVersion version) {
            this.version = version
        }

        @Override
        protected void afterInit() {
            if (!AbstractGradleCrossVersionTest.isAssignableFrom(target)) {
                throw new IllegalStateException(String.format("Expected test class extending %s, got %s", AbstractGradleCrossVersionTest.simpleName, target.simpleName))
            }
        }

        @Override
        protected void before(IMethodInvocation invocation) {
            super.before(invocation)
            AbstractGradleCrossVersionTest.primaryGradleVersion = version;
        }

        @Override
        protected String getDisplayName() {
            version.toString()
        }
    }
}
