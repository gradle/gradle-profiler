package org.gradle.profiler.ide;

public interface CommandChain {
    CommandChain importGradleProject();
    CommandChain waitForSmartMode();
    CommandChain exitApp();
    void run();
}
