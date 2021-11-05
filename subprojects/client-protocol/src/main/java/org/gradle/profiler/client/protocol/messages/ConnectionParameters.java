package org.gradle.profiler.client.protocol.messages;

import org.gradle.profiler.client.protocol.messages.Message;

import java.io.File;

public class ConnectionParameters implements Message {
    private final File gradleInstallation;

    public ConnectionParameters(File gradleInstallation) {
        this.gradleInstallation = gradleInstallation;
    }

    public File getGradleInstallation() {
        return gradleInstallation;
    }
}
