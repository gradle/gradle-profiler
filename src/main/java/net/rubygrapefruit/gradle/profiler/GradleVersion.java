package net.rubygrapefruit.gradle.profiler;

import java.io.File;

class GradleVersion {
    private final String version;
    private final File gradleHome;

    public GradleVersion(String version, File gradleHome) {
        this.version = version;
        this.gradleHome = gradleHome;
    }

    public String getVersion() {
        return version;
    }

    public File getGradleHome() {
        return gradleHome;
    }
}
