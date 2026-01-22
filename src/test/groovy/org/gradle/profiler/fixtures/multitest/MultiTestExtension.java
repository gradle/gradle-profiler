package org.gradle.profiler.fixtures.multitest;

import org.spockframework.runtime.extension.IAnnotationDrivenExtension;
import org.spockframework.runtime.model.FeatureInfo;
import org.spockframework.runtime.model.SpecInfo;

import java.lang.annotation.Annotation;

public abstract class MultiTestExtension<T extends Annotation> implements IAnnotationDrivenExtension<T> {

    @Override
    public void visitSpecAnnotation(T annotation, SpecInfo spec) {
        if (!spec.getFeatures().isEmpty()) {
            AbstractMultiTestInterceptor interceptor = makeInterceptor(spec.getBottomSpec().getReflection());
            for (FeatureInfo feature : spec.getFeatures()) {
                interceptor.interceptFeature(feature);
            }
        }
    }

    protected abstract AbstractMultiTestInterceptor makeInterceptor(Class<?> testClass);
}
