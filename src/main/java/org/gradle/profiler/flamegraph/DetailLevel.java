package org.gradle.profiler.flamegraph;

import java.util.Arrays;
import java.util.List;

public enum DetailLevel {
    RAW(
        true,
        true,
        Arrays.asList("--minwidth", "0.5"),
        Arrays.asList("--minwidth", "1")
    ),
    SIMPLIFIED(
        false,
        false,
        Arrays.asList("--minwidth", "1"),
        Arrays.asList("--minwidth", "2")
    );

    private final boolean showArguments;
    private final boolean showLineNumbers;
    private final List<String> flameGraphOptions;
    private final List<String> icicleGraphOptions;

    DetailLevel(boolean showArguments, boolean showLineNumbers, List<String> flameGraphOptions, List<String> icicleGraphOptions) {
        this.showArguments = showArguments;
        this.showLineNumbers = showLineNumbers;
        this.flameGraphOptions = flameGraphOptions;
        this.icicleGraphOptions = icicleGraphOptions;
    }

    public boolean isShowArguments() {
        return showArguments;
    }

    public boolean isShowLineNumbers() {
        return showLineNumbers;
    }

    public List<String> getFlameGraphOptions() {
        return flameGraphOptions;
    }

    public List<String> getIcicleGraphOptions() {
        return icicleGraphOptions;
    }
}
