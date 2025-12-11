package org.gradle.profiler.fixtures.compatibility.gradle;

import org.gradle.profiler.fixtures.multitest.AbstractMultiTestInterceptor;
import org.gradle.profiler.fixtures.multitest.MultiTestExtension;

public class GradleCrossVersionTestExtension extends MultiTestExtension<GradleCrossVersionTest> {

    @Override
    protected AbstractMultiTestInterceptor makeInterceptor(Class<?> testClass) {
        return new GradleCrossVersionTestInterceptor(testClass);
    }
}
