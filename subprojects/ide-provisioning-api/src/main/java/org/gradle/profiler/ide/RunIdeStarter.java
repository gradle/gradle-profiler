package org.gradle.profiler.ide;

public interface RunIdeStarter {
    RunIdeContext newContext(String projectLocation, String ideLocation);
}
