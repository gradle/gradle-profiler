package org.gradle.profiler;

public interface BuildMutator {
    /**
     * Runs before the first iteration of the scenario.
     */
    default void beforeScenario() {};

    /**
     * Runs before each iteration if cleanup tasks are declared.
     */
    default void beforeCleanup() {};

    /**
     * Runs after each iteration of cleanup has finished.
     */
    default void afterCleanup(Throwable error) {};

    /**
     * Runs before starting an iteration of the build, after any potential cleanup has finished.
     */
    default void beforeBuild() {};

    /**
     * Runs after each iteration of the build has finished.
     */
    default void afterBuild(Throwable error) {};

    /**
     * Runs after the last iteration of the scenario has finished.
     */
    default void afterScenario() {};
}
