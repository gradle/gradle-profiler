package org.gradle.profiler;

import java.util.List;

/**
 * Runs some particular action against a build.
 */
public interface BuildAction {
    /**
     * A human consumable display name for this action.
     */
    String getDisplayName();

    /**
     * Runs the measured work of this action.
     */
    void run(BuildInvoker buildInvoker, List<String> tasks, List<String> gradleArgs, List<String> jvmArgs);
}
