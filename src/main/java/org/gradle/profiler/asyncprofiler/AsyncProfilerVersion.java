package org.gradle.profiler.asyncprofiler;

/**
 * Async Profiler Version.
 * <p>
 * The versions are usually in the form <code>major.minor</code>, like {@code 3.0} or {@code 4.2}.
 * Occasionally, there are patch releases like {@code 4.2.1}.
 */
public class AsyncProfilerVersion implements Comparable<AsyncProfilerVersion> {

    private final int major;
    private final int minor;
    private final int patch;
    private final String stringVersion;

    public AsyncProfilerVersion(String stringVersion) {
        String[] parts = stringVersion.split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid version: " + stringVersion);
        }
        try {
            major = Integer.parseInt(parts[0]);
            minor = Integer.parseInt(parts[1]);
            patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid version: " + stringVersion, e);
        }
        this.stringVersion = stringVersion;
    }

    public String getAsString() {
        return stringVersion;
    }

    @Override
    public int compareTo(AsyncProfilerVersion other) {
        int majorDiff = Integer.compare(this.major, other.major);
        if (majorDiff != 0) {
            return majorDiff;
        }
        int minorDiff = Integer.compare(this.minor, other.minor);
        if (minorDiff != 0) {
            return minorDiff;
        }
        return Integer.compare(this.patch, other.patch);
    }

    @Override
    public String toString() {
        return getAsString();
    }
}
