package org.gradle.trace.listener;

import org.gradle.api.invocation.Gradle;
import org.gradle.trace.TraceResult;

public interface BuildOperationListenerAdapter {
    static BuildOperationListenerAdapter create(Gradle gradle, TraceResult traceResult) {
        String gradleVersion = gradle.getGradleVersion();
        if (gradleVersion.startsWith("3.3") || gradleVersion.startsWith("3.4")) {
            return new Gradle33BuildOperationListenerAdapter(gradle, new Gradle33BuildOperationListenerInvocationHandler(traceResult));
        }
        if (gradleVersion.startsWith("3.5")) {
            return new Gradle35BuildOperationListenerAdapter(gradle, new Gradle33BuildOperationListenerInvocationHandler(traceResult));
        }
        if (gradleVersion.startsWith("4.")) {
            return new Gradle35BuildOperationListenerAdapter(gradle, new Gradle40BuildOperationListenerInvocationHandler(traceResult));
        }
        throw new IllegalStateException("Gradle version " + gradleVersion + " not supported, 3.3+ required");
    }

    void remove();
}
