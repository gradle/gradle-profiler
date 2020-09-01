package org.gradle.profiler;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.hash.Hashing;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class DefaultScenarioContext implements ScenarioContext {
    private final UUID invocationId;
    private final String scenarioName;

    @VisibleForTesting
    public DefaultScenarioContext(UUID invocationId, String scenarioName) {
        this.invocationId = invocationId;
        this.scenarioName = scenarioName;
    }

    @Override
    public String getUniqueScenarioId() {
        return String.format("_%s_%s", invocationId.toString().replaceAll("-", "_"), mangleName(scenarioName));
    }

    @Override
    public BuildContext withBuild(Phase phase, int count) {
        return new DefaultBuildContext(this, phase, count);
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
