package org.gradle.profiler.report;

public enum Format {
    LONG, WIDE;

    public static Format parse(String name) {
        for (Format format : values()) {
            if (format.name().toLowerCase().equals(name)) {
                return format;
            }
        }
        throw new IllegalArgumentException("Unknown CSV format: " + name);
    }
}
