package org.gradle.profiler.jprofiler;

import org.gradle.profiler.OperatingSystem;
import org.gradle.profiler.ScenarioSettings;

import java.io.File;

import static org.gradle.profiler.OperatingSystem.MAC_OS_APPLICATIONS_PATH;

public class JProfiler {

    private static final String MAJOR_VERSION = "10";

    public static String getDefaultHomeDir() {
        if (OperatingSystem.isWindows()) {
            return "c:\\Program Files\\jprofiler" + MAJOR_VERSION;
        } else if (OperatingSystem.isMacOS()) {
            return MAC_OS_APPLICATIONS_PATH + "/JProfiler.app";
        }
        return "/opt/jprofiler" + MAJOR_VERSION;
    }

    public static String getSnapshotPath(ScenarioSettings settings) {
        int i = 0;
        File snapshotFile;
        do {
            snapshotFile = settings.profilerOutputLocationFor(( i == 0 ? "" : ("_" + i)) + ".jps");
            ++i;
        } while (snapshotFile.exists());
        return snapshotFile.getAbsolutePath();
    }
}
