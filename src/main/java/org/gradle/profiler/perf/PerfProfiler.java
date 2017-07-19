package org.gradle.profiler.perf;

import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpecBuilder;
import org.gradle.profiler.JvmArgsCalculator;
import org.gradle.profiler.Profiler;
import org.gradle.profiler.ProfilerController;
import org.gradle.profiler.ScenarioSettings;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class PerfProfiler extends Profiler {
    private final PerfProfilerArgs perfProfilerArgs;

    private ArgumentAcceptingOptionSpec<Integer> frequency;
    private ArgumentAcceptingOptionSpec<Integer> maxStack;

    public PerfProfiler() {
        this(null);
    }

    private PerfProfiler(PerfProfilerArgs perfProfilerArgs) {
        this.perfProfilerArgs = perfProfilerArgs;
    }

    @Override
    public String toString() {
        return "Perf profiler";
    }

    @Override
    public List<String> summarizeResultFile(File resultFile) {
        if (resultFile.getName().endsWith(".svg")) {
            return Collections.singletonList(resultFile.getAbsolutePath());
        }
        return null;
    }

    @Override
    public Profiler withConfig(OptionSet parsedOptions) {
        return new PerfProfiler(newConfigObject(parsedOptions));
    }

    private PerfProfilerArgs newConfigObject(final OptionSet parsedOptions) {
        return new PerfProfilerArgs(
                parsedOptions.valueOf(frequency),
                parsedOptions.valueOf(maxStack));
    }

    @Override
    public ProfilerController newController(final String pid, final ScenarioSettings settings) {
        return new PerfProfilerController(perfProfilerArgs, settings);
    }

    @Override
    public void addOptions(final OptionParser parser) {
        frequency = parser.accepts("perf-frequency", "Profile at this frequency")
                .availableIf("profile")
                .withOptionalArg()
                .ofType(Integer.class)
                .defaultsTo(99);
        maxStack = parser.accepts("perf-max-stack", "The stack depth limit")
                .availableIf("profile")
                .withOptionalArg()
                .ofType(Integer.class)
                .defaultsTo(1024);
    }

    @Override
    public JvmArgsCalculator newJvmArgsCalculator(ScenarioSettings settings) {
        return new PerfJvmArgsCalculator();
    }
}
