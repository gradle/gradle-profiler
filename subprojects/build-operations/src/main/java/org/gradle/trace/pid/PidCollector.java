package org.gradle.trace.pid;

import org.gradle.api.JavaVersion;
import org.gradle.api.internal.GradleInternal;
import org.gradle.internal.nativeintegration.ProcessEnvironment;
import org.gradle.util.GradleVersion;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@SuppressWarnings("unused")
public class PidCollector {
    public static void collect(GradleInternal gradle, File outFile) {
        // Always write the file, whether we register a build service or not, so we also have a PID file for TAPI model requests.
        ProcessEnvironment processEnvironment = gradle.getServices().get(ProcessEnvironment.class);
        try {
            Files.write(outFile.toPath(), processEnvironment.getPid().toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (GradleVersion.current().compareTo(GradleVersion.version("6.1")) >= 0) {
            if (JavaVersion.current().isJava9Compatible()) {
                AbstractPidCollectorBuildService.registerBuildService(PidCollectorBuildService.class, gradle, outFile);
            } else {
                AbstractPidCollectorBuildService.registerBuildService(PidCollectorJava8BuildService.class, gradle, outFile);
            }
        }
    }
}
