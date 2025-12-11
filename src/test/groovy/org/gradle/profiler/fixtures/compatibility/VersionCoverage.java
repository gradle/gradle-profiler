package org.gradle.profiler.fixtures.compatibility;

import javax.annotation.Nullable;

public enum VersionCoverage {

    DEFAULT("default"),
    LATEST("latest"),
    PARTIAL("partial"),
    FULL("all"),
    UNKNOWN(null);

    private final String selector;

    VersionCoverage(String selector) {
        this.selector = selector;
    }

    static VersionCoverage from(String requested) {
        for (VersionCoverage context : values()) {
            if (context != UNKNOWN && context.selector.equals(requested)) {
                return context;
            }
        }
        return UNKNOWN;
    }

    @Nullable
    public String getSelector() {
        return selector;
    }
}
