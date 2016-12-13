package org.gradle.profiler;

public class NoOpBuildMutator implements BuildMutator {
    @Override
    public void beforeBuild() {
    }

    @Override
    public void cleanup() {
    }
}
