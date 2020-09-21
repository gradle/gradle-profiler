package org.gradle.profiler.client.protocol;

import java.util.List;

public class SyncParameters extends Message {
    private final List<String> gradleArgs;
    private final List<String> jvmArgs;

    public SyncParameters(List<String> gradleArgs, List<String> jvmArgs) {
        this.gradleArgs = gradleArgs;
        this.jvmArgs = jvmArgs;
    }

    public List<String> getGradleArgs() {
        return gradleArgs;
    }

    public List<String> getJvmArgs() {
        return jvmArgs;
    }
}
