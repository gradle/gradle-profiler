package org.gradle.profiler;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.hash.Hashing;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class ScenarioContext {
    private final UUID invocationId;
    private final String scenarioName;

    public static ScenarioContext from(InvocationSettings invocationSettings, ScenarioDefinition scenarioDefinition) {
        return new ScenarioContext(invocationSettings.getInvocationId(), scenarioDefinition.getName());
    };

    @VisibleForTesting
    public ScenarioContext(UUID invocationId, String scenarioName) {
        this.invocationId = invocationId;
        this.scenarioName = scenarioName;
    }

    public String getUniqueScenarioId() {
        return String.format("_%s_%s", invocationId.toString().replaceAll("-", "_"), mangleName(scenarioName));
    }

    public BuildContext withBuild(Phase phase, int count) {
        return new BuildContext(invocationId, scenarioName, phase, count);
    }

    /**
     * This is to ensure that the scenario ID is a valid Java identifier part, and it is also (reasonably) unique.
     */
    private static String mangleName(String scenarioName) {
        StringBuilder name = new StringBuilder();
        for (char ch :scenarioName.toCharArray()){
            name.append(Character.isJavaIdentifierPart(ch) ? ch : '_');
        }
        name.append('_');
        name.append(Hashing.murmur3_32().hashString(scenarioName, StandardCharsets.UTF_8));
        return name.toString();
    }
}
