package org.gradle.profiler;

import java.util.List;

public interface JvmArgsCalculator {
    JvmArgsCalculator DEFAULT = jvmArgs -> {
    };

    void calculateJvmArgs(List<String> jvmArgs);
}
