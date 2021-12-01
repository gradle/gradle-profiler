package org.gradle.profiler.studio.invoker;

import org.gradle.profiler.result.BuildInvocationResult;
import org.gradle.profiler.result.Sample;

import java.time.Duration;

public enum StudioSample implements Sample<BuildInvocationResult> {
    GRADLE_EXECUTION_TIME("Gradle execution time") {
        @Override
        public Duration extractFrom(BuildInvocationResult result) {
            StudioBuildActionResult studioResult = (StudioBuildActionResult) result.getActionResult();
            return studioResult.getGradleTotalExecutionTime();
        }
    },
    IDE_EXECUTION_TIME("IDE execution time") {
        @Override
        public Duration extractFrom(BuildInvocationResult result) {
            StudioBuildActionResult studioResult = (StudioBuildActionResult) result.getActionResult();
            return studioResult.getIdeExecutionTime();
        }
    };

    private final String name;

    StudioSample(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }
}
