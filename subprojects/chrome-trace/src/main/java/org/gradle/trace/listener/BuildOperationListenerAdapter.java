package org.gradle.trace.listener;

import static org.gradle.util.GradleVersion.version;

import org.gradle.api.invocation.Gradle;
import org.gradle.trace.TraceResult;
import org.gradle.util.GradleVersion;

public interface BuildOperationListenerAdapter {
    static BuildOperationListenerAdapter create(Gradle gradle, TraceResult traceResult) {
        GradleVersion gradleVersion = version(gradle.getGradleVersion()).getBaseVersion();
        if (inVersionRange(gradleVersion, "3.3", "3.5")) {
            return new Gradle33BuildOperationListenerAdapter(gradle, new Gradle33BuildOperationListenerInvocationHandler(traceResult));
        }
        if (inVersionRange(gradleVersion, "3.5", "4.0-milestone-2")) {
            return new Gradle35BuildOperationListenerAdapter(gradle, new Gradle33BuildOperationListenerInvocationHandler(traceResult));
        }
        if (gradleVersion.equals(version("4.0-milestone-2"))) {
            return new Gradle35BuildOperationListenerAdapter(gradle, new Gradle40BuildOperationListenerInvocationHandler(traceResult));
        }
        if (inVersionRange(gradleVersion, "4.0", "4.7")) {
            return new Gradle40BuildOperationListenerAdapter(gradle, new Gradle40BuildOperationListenerInvocationHandler(traceResult));
        }
        if (inVersionRange(gradleVersion, "4.7", "5.0")) {
            return new Gradle47BuildOperationListenerAdapter(gradle, new Gradle47BuildOperationListenerInvocationHandler(traceResult));
        }
        if (gradleVersion.compareTo(version("5.0")) >= 0) {
            return new Gradle47BuildOperationListenerAdapter(gradle, new Gradle50BuildOperationListenerInvocationHandler(traceResult));
        }
        throw new IllegalStateException("Gradle version " + gradleVersion + " not supported, 3.3+ required");
    }

    static boolean inVersionRange(GradleVersion gradleVersion, String lower, String upper) {
        return gradleVersion.compareTo(version(lower)) >= 0 && gradleVersion.compareTo(version(upper)) < 0;
    }

    void remove();
}
