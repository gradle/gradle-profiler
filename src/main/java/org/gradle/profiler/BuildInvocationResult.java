package org.gradle.profiler;

import com.google.common.collect.ImmutableList;

import java.time.Duration;
import java.util.List;

public class BuildInvocationResult {
    private final String displayName;
    private final Sample executionTime;

    public BuildInvocationResult(String displayName, Duration executionTime) {
        this.displayName = displayName;
        this.executionTime = new Sample("execution", executionTime);
    }

    public String getDisplayName() {
        return displayName;
    }

    public Sample getExecutionTime() {
        return executionTime;
    }

    public List<Sample> getSamples() {
        return ImmutableList.of(executionTime);
    }

    public static class Sample {
        private final String name;
        private final Duration duration;

        public Sample(String name, Duration duration) {
            this.name = name;
            this.duration = duration;
        }

        public String getName() {
            return name;
        }

        public Duration getDuration() {
            return duration;
        }
    }
}
