package org.gradle.profiler.jfr;

import org.gradle.api.JavaVersion;
import org.gradle.profiler.JvmArgsCalculator;

import java.util.List;
import java.util.Locale;

public class JFRJvmArgsCalculator implements JvmArgsCalculator {
    @Override
    public void calculateJvmArgs(List<String> jvmArgs) {
        if (!JavaVersion.current().isJava11Compatible()) {
            if (!isOracleVm()) {
                throw new IllegalArgumentException("JFR is only supported on OpenJDK since Java 11 and Oracle JDK since Java 7");
            }
            jvmArgs.add("-XX:+UnlockCommercialFeatures");
        }
        jvmArgs.add("-XX:+FlightRecorder");
        jvmArgs.add("-XX:FlightRecorderOptions=stackdepth=1024");
        jvmArgs.add("-XX:+UnlockDiagnosticVMOptions");
        jvmArgs.add("-XX:+DebugNonSafepoints");
    }

    private boolean isOracleVm() {
        return System.getProperty("java.vendor").toLowerCase(Locale.ROOT).contains("oracle");
    }
}
