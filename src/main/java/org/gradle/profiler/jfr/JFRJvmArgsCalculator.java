package org.gradle.profiler.jfr;

import org.gradle.api.JavaVersion;
import org.gradle.profiler.JvmArgsCalculator;

import java.io.File;
import java.util.List;
import java.util.Locale;

public class JFRJvmArgsCalculator implements JvmArgsCalculator {
    private final JFRArgs args;
    private final boolean profileOnStart;
    private final boolean captureOnExit;
    private final File outputFile;

    public JFRJvmArgsCalculator(JFRArgs args, boolean profileOnStart, boolean captureOnExit, File outputFile) {
        this.args = args;
        this.profileOnStart = profileOnStart;
        this.captureOnExit = captureOnExit;
        this.outputFile = outputFile;
    }

    @Override
    public void calculateJvmArgs(List<String> jvmArgs) {
        boolean java11OrLater = JavaVersion.current().isJava11Compatible();
        if (!java11OrLater) {
            if (!isOracleVm()) {
                throw new IllegalArgumentException("JFR is only supported on OpenJDK since Java 11 and Oracle JDK since Java 7");
            }
            jvmArgs.add("-XX:+UnlockCommercialFeatures");
        }
        jvmArgs.add("-XX:+FlightRecorder");
        jvmArgs.add("-XX:+UnlockDiagnosticVMOptions");
        jvmArgs.add("-XX:+DebugNonSafepoints");

        StringBuilder flightRecorderOptions = new StringBuilder("-XX:FlightRecorderOptions=stackdepth=1024");

        if (profileOnStart) {
            if (java11OrLater) {
                String dumpOnExit = captureOnExit
                    ? ",dumponexit=true,filename=" + outputFile.getParentFile().getAbsolutePath()
                    : "";
                jvmArgs.add("-XX:StartFlightRecording=name=profile,settings=" + args.getJfrSettings() + dumpOnExit);
            } else {
                jvmArgs.add("-XX:StartFlightRecording=name=profile,settings=" + args.getJfrSettings());
                if (captureOnExit) {
                    flightRecorderOptions.append(",defaultrecording=true,dumponexit=true")
                        .append(",dumponexitpath=")
                        .append(outputFile.getParentFile().getAbsolutePath());
                }
            }
        }

        jvmArgs.add(flightRecorderOptions.toString());
    }

    private boolean isOracleVm() {
        return System.getProperty("java.vendor").toLowerCase(Locale.ROOT).contains("oracle");
    }
}
