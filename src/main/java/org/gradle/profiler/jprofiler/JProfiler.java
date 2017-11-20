package org.gradle.profiler.jprofiler;

import org.gradle.profiler.OperatingSystem;
import org.gradle.profiler.ScenarioSettings;

import java.io.File;

public class JProfiler {

    private static final String MAJOR_VERSION = "10";

    public static String getDefaultHomeDir() {
        if (OperatingSystem.isWindows()) {
            return "c:\\Program Files\\jprofiler" + MAJOR_VERSION;
        } else if (OperatingSystem.isMacOS()) {
            return "/Applications/JProfiler.app";
        } else {
            return "/opt/jprofiler" + MAJOR_VERSION;
        }
    }

    public static String getSnapshotPath(ScenarioSettings settings) {
        File outputDir = settings.getScenario().getOutputDir();
        String snapshotName = settings.getScenario().getProfileName();

        int i = 0;
        File snapshotFile;
        do {
            snapshotFile = new File(outputDir, snapshotName  + ( i == 0 ? "" : ("_" + i)) + ".jps");
            ++i;
        } while (snapshotFile.exists());
        return snapshotFile.getAbsolutePath();
    }
}
