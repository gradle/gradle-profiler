package org.gradle.profiler;

import static org.gradle.profiler.ScenarioLoader.BAZEL;
import static org.gradle.profiler.ScenarioLoader.BUCK;
import static org.gradle.profiler.ScenarioLoader.MAVEN;

import com.typesafe.config.Config;
import javax.annotation.Nullable;

public class ScenarioUtil {

    /**
     * Returns the specific config for type of build that is running,
     * if the scenario doesn't define the build, then this default value is returned
     *
     * @param rootScenario the root scenario config
     * @param settings     the invocation settings that indicates the type of build selected
     * @param defaultScenario if the scenario doesn't define the build, then this default value is returned, this can be null.
     * @return if the build config exists or if the scenario doesn't define the build, then this default value is returned
     */
    public static Config getBuildConfig(Config rootScenario, InvocationSettings settings, @Nullable
        Config defaultScenario) {
        Config scenario;
        if (settings.isBazel()) {
            scenario = getConfigOrDefault(rootScenario, BAZEL, defaultScenario);
        } else if (settings.isBuck()) {
            scenario = getConfigOrDefault(rootScenario, BUCK, defaultScenario);
        } else if (settings.isMaven()) {
            scenario = getConfigOrDefault(rootScenario, MAVEN, defaultScenario);
        } else {
            scenario = rootScenario;
        }
        return scenario;
    }

    private static Config getConfigOrDefault(Config rootScenario, String key, @Nullable Config defaultScenario) {
        Config scenario;
        if (rootScenario.hasPath(key)) {
            scenario = rootScenario.getConfig(key);
        } else {
            scenario = defaultScenario;
        } return scenario;
    }
}
