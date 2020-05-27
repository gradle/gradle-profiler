package org.gradle.trace.pid;

import org.gradle.api.internal.GradleInternal;
import org.gradle.internal.nativeintegration.ProcessEnvironment;
import org.gradle.util.GFileUtils;

import java.io.File;

@SuppressWarnings("unused")
public class PidCollector {
    public static void collect(GradleInternal gradle, File outFile) {
        ProcessEnvironment processEnvironment = gradle.getServices().get(ProcessEnvironment.class);
        GFileUtils.writeFile(processEnvironment.getPid().toString(), outFile);
    }
}
