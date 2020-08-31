package org.gradle.profiler;

public interface BuildContext extends ScenarioContext {
    String getUniqueBuildId();

    Phase getPhase();

    int getIteration();

    String getDisplayName();
}
