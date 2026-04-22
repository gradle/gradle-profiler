package org.gradle.profiler;

public interface BuildMutator {
    /**
     * Validates if the mutator works with the given invoker.
     */
    default void validate(BuildInvoker invoker) {}

    /**
     * Returns true if this mutator applies source changes to measure incremental build performance.
     * Such mutators require at least one warm-up build to establish baseline state.
     */
    default boolean requiresBaseline() {
        return false;
    }

    /**
     * Runs before the first iteration of the scenario.
     */
    default void beforeScenario(ScenarioContext context) {}

    /**
     * Runs before each iteration if cleanup tasks are declared.
     */
    default void beforeCleanup(BuildContext context) {}

    /**
     * Runs after each iteration of cleanup has finished.
     */
    default void afterCleanup(BuildContext context, Throwable error) {}

    /**
     * Runs before starting an iteration of the build, after any potential cleanup has finished.
     */
    default void beforeBuild(BuildContext context) {}

    /**
     * Runs after each iteration of the build has finished.
     */
    default void afterBuild(BuildContext context, Throwable error) {}

    /**
     * Runs after the last iteration of the scenario has finished.
     */
    default void afterScenario(ScenarioContext context) {}

    BuildMutator NOOP = new BuildMutator() {
        @Override
        public String toString() {
            return "none";
        }
    };
}
