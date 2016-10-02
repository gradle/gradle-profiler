package net.rubygrapefruit.gradle.profiler;

import java.util.List;

class JFRJvmArgsCalculator extends JvmArgsCalculator{
    @Override
    void calculateJvmArgs(List<String> jvmArgs) {
        jvmArgs.add("-XX:+UnlockCommercialFeatures");
        jvmArgs.add("-XX:+FlightRecorder");
        jvmArgs.add("-XX:FlightRecorderOptions=stackdepth=1024");
        jvmArgs.add("-XX:+UnlockDiagnosticVMOptions");
        jvmArgs.add("-XX:+DebugNonSafepoints");
    }
}
