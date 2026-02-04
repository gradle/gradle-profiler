package org.gradle.profiler.lifecycle.agent.strategy;

/**
 * Metadata describing an interception point in the Gradle build lifecycle.
 */
public interface HeapDumpStrategy {
    /**
     * @return the option value used in CLI (e.g., "config-end", "build-end")
     */
    String getOptionValue();

    /**
     * @return the file prefix for heap dump files (e.g., "gradle-config-end")
     */
    String getFilePrefix();

    /**
     * @return the message to display when the interception point is reached
     */
    String getInterceptionMessage();

    /**
     * @return the name of the method to intercept in DefaultBuildLifecycleController
     */
    String getTargetMethodName();

    /**
     * @return the descriptor of the method to intercept
     */
    String getTargetMethodDescriptor();
}
