package org.gradle.profiler.mutations;

import org.gradle.profiler.BuildMutator;

public class AbstractBuildMutator implements BuildMutator {
    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}
