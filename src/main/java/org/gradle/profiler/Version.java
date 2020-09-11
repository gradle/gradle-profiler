package org.gradle.profiler;

public class Version {
    public static String getVersion() {
        String version = Version.class.getPackage().getImplementationVersion();
        return version != null
            ? version
            : "UNKNOWN";
    }
}
