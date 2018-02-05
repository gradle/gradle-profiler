package org.gradle.profiler;

import java.io.IOException;

public interface BuildMutator {
    /**
     * Runs before the first iteration of the scenario.
     */
    default void beforeScenario() throws IOException {};

    /**
     * Runs before each iteration if cleanup tasks are declared.
     */
    default void beforeCleanup() throws IOException {};

    /**
     * Runs before starting an iteration of the build, after any potential cleanup has finished.
     */
    default void beforeBuild() throws IOException {};

    /**
     * Runs after each iteration of the build has finished.
     */
    default void afterBuild() throws IOException {};

    /**
     * Runs after the last iteration of the scenario has finished.
     */
    default void afterScenario() throws IOException {};
}
