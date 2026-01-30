package org.gradle.profiler.fixtures.file

import org.spockframework.runtime.AbstractRunListener
import org.spockframework.runtime.GroovyRuntimeUtil
import org.spockframework.runtime.extension.IAnnotationDrivenExtension
import org.spockframework.runtime.extension.IMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation
import org.spockframework.runtime.model.ErrorInfo
import org.spockframework.runtime.model.FeatureInfo
import org.spockframework.runtime.model.SpecInfo

class CleanupTestDirectoryExtension implements IAnnotationDrivenExtension<CleanupTestDirectory> {
    @Override
    void visitSpecAnnotation(CleanupTestDirectory annotation, SpecInfo spec) {
        spec.features.each { FeatureInfo feature ->
            feature.addIterationInterceptor(new FailureCleanupInterceptor(annotation.value()))
        }
    }

    private static class FailureCleanupInterceptor implements IMethodInterceptor {
        final String fieldName

        FailureCleanupInterceptor(String fieldName) {
            this.fieldName = fieldName
        }

        @Override
        void intercept(IMethodInvocation invocation) throws Throwable {
            TestDirectoryProvider provider = GroovyRuntimeUtil.getProperty(invocation.instance, fieldName) as TestDirectoryProvider
            def noCleanupOnErrorListener = new AbstractRunListener() {
                @Override
                void error(ErrorInfo error) {
                    provider.suppressCleanup()
                }
            }
            def spec = invocation.spec
            while (spec != null) {
                spec.addListener(noCleanupOnErrorListener)
                spec = spec.subSpec
            }
            invocation.proceed()
        }
    }
}
