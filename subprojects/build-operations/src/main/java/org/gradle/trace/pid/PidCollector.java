package org.gradle.trace.pid;

import org.gradle.api.internal.GradleInternal;
import org.gradle.internal.nativeintegration.ProcessEnvironment;
import org.gradle.util.GFileUtils;
import org.gradle.util.GradleVersion;

import java.io.File;

@SuppressWarnings("unused")
public class PidCollector {
    public static void collect(GradleInternal gradle, File outFile) {
        // Always write the file, whether we register a build service or not, so we also have a PID file for TAPI model requests.
        ProcessEnvironment processEnvironment = gradle.getServices().get(ProcessEnvironment.class);
        GFileUtils.writeFile(processEnvironment.getPid().toString(), outFile);
        if (GradleVersion.current().compareTo(GradleVersion.version("6.1")) >= 0) {
            PidCollectorBuildService.registerBuildService(gradle, outFile);
        }
    }
}
