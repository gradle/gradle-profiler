package org.gradle.profiler.flamegraph;

public enum DetailLevel {
    RAW(
        true,
        true
    ),
    SIMPLIFIED(
        false,
        false
    );

    private final boolean showArguments;
    private final boolean showLineNumbers;

    DetailLevel(boolean showArguments, boolean showLineNumbers) {
        this.showArguments = showArguments;
        this.showLineNumbers = showLineNumbers;
    }

    public boolean isShowArguments() {
        return showArguments;
    }

    public boolean isShowLineNumbers() {
        return showLineNumbers;
    }

}
