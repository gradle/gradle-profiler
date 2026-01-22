package org.gradle.profiler.result;

public abstract class Sample<T extends BuildInvocationResult> {
    private final String name;
    private final String unit;

    public Sample(String name, String unit) {
        this.name = name;
        this.unit = unit;
    }

    public String getName() {
        return name;
    }

    public String getUnit() {
        return unit;
    }

    public abstract double extractValue(T result);

    public abstract int extractTotalCountFrom(T result);
}
