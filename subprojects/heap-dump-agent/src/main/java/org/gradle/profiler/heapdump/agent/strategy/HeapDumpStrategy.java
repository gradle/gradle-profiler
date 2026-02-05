package org.gradle.profiler.heapdump.agent.strategy;

/**
 * Metadata describing an interception point in the Gradle build lifecycle.
 */
public interface HeapDumpStrategy {
    /**
     * @return the option value used in CLI (e.g., "config-end", "build-end")
     */
    String getName();

    /**
     * @return the internal name of the class to intercept (e.g., "org/gradle/internal/build/DefaultBuildLifecycleController")
     */
    String getTargetClassName();

    /**
     * @return the name of the method to intercept
     */
    String getTargetMethodName();

    /**
     * @return the descriptor of the method to intercept
     */
    String getTargetMethodDescriptor();
}
