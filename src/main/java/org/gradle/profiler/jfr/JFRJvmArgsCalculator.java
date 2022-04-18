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
        boolean oracleVm = isOracleVm();
        boolean java8OrLater = JavaVersion.current().isJava8Compatible();
        boolean java11OrLater = JavaVersion.current().isJava11Compatible();
        if (oracleVm && !java11OrLater) {
            jvmArgs.add("-XX:+UnlockCommercialFeatures");
        } else if (!java8OrLater) {
            throw new IllegalArgumentException("JFR is only supported on OpenJDK since Java 8 and Oracle JDK since Java 7");
        }
        jvmArgs.add("-XX:+FlightRecorder");
        jvmArgs.add("-XX:+UnlockDiagnosticVMOptions");
        jvmArgs.add("-XX:+DebugNonSafepoints");

        StringBuilder flightRecorderOptions = new StringBuilder("-XX:FlightRecorderOptions=stackdepth=1024");

        if (profileOnStart) {
            if (oracleVm && !java11OrLater) {
                jvmArgs.add("-XX:StartFlightRecording=name=profile,settings=" + args.getJfrSettings());
                if (captureOnExit) {
                    flightRecorderOptions.append(",defaultrecording=true,dumponexit=true")
                        .append(",dumponexitpath=")
                        .append(outputFile.getAbsolutePath());
                }
            } else {
                String dumpOnExit = captureOnExit
                    ? ",dumponexit=true,filename=" + outputFile.getAbsolutePath()
                    : "";
                jvmArgs.add("-XX:StartFlightRecording=name=profile,settings=" + args.getJfrSettings() + dumpOnExit);
            }
        }

        jvmArgs.add(flightRecorderOptions.toString());
    }

    private boolean isOracleVm() {
        return System.getProperty("java.vendor").toLowerCase(Locale.ROOT).contains("oracle");
    }
}
