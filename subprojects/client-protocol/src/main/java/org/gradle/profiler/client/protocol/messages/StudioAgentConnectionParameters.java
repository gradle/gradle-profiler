package org.gradle.profiler.client.protocol.messages;

import java.io.File;

public class StudioAgentConnectionParameters implements Message {
    private final File gradleInstallation;

    public StudioAgentConnectionParameters(File gradleInstallation) {
        this.gradleInstallation = gradleInstallation;
    }

    public File getGradleInstallation() {
        return gradleInstallation;
    }
}
