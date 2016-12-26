package org.gradle.profiler.jprofiler;

import org.gradle.profiler.OperatingSystem;

public class JProfiler {

    private static final String MAJOR_VERSION = "9";

    public static String getDefaultHomeDir() {
        if (OperatingSystem.isWindows()) {
            return "c:\\Program Files\\jprofiler" + MAJOR_VERSION;
        } else if (OperatingSystem.isMacOS()) {
            return "/Applications/JProfiler.app";
        } else {
            return "/opt/jprofiler" + MAJOR_VERSION;
        }
    }

}
