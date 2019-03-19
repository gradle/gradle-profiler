package org.gradle.profiler.asyncprofiler;

import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.ValueConverter;
import org.gradle.profiler.*;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.gradle.profiler.asyncprofiler.AsyncProfilerConfig.Counter;

public class AsyncProfiler extends InstrumentingProfiler {

    static final String ASYNC_PROFILER_HOME = "ASYNC_PROFILER_HOME";
    private final AsyncProfilerConfig config;

    private ArgumentAcceptingOptionSpec<File> profilerHomeOption;
    private ArgumentAcceptingOptionSpec<String> eventOption;
    private ArgumentAcceptingOptionSpec<Counter> counterOption;
    private ArgumentAcceptingOptionSpec<Integer> frameBufferOption;
    private ArgumentAcceptingOptionSpec<Integer> intervalOption;
    private ArgumentAcceptingOptionSpec<Integer> stackDepthOption;
    private ArgumentAcceptingOptionSpec<Boolean> systemThreadOption;

    public AsyncProfiler() {
        this(null);
    }

    private AsyncProfiler(AsyncProfilerConfig config) {
        this.config = config;
    }


    @Override
    public void addOptions(OptionParser parser) {
        profilerHomeOption = parser.accepts("async-profiler-home", "Async Profiler home directory")
            .availableIf("profile")
            .withRequiredArg()
            .ofType(File.class);
        eventOption = parser.accepts("async-profiler-event", "The event to sample, e.g. 'cpu' or 'alloc'.")
            .availableIf("profile")
            .withRequiredArg()
            .defaultsTo("cpu");
        counterOption = parser.accepts("async-profiler-counter", "The counter to use, either 'samples' or 'totals'")
            .availableIf("profile")
            .withRequiredArg()
            .withValuesConvertedBy(new CounterConverter())
            .defaultsTo(Counter.SAMPLES);
        intervalOption = parser.accepts("async-profiler-interval", "The sampling interval in nanoseconds.")
            .availableIf("profile")
            .withRequiredArg()
            .ofType(Integer.class)
            .defaultsTo(10_000_000);
        stackDepthOption = parser.accepts("async-profiler-stackdepth", "The maximum Java stack depth.")
            .availableIf("profile")
            .withRequiredArg()
            .ofType(Integer.class)
            .defaultsTo(2048);
        frameBufferOption = parser.accepts("async-profiler-framebuffer", "The size of the frame buffer in bytes.")
            .availableIf("profile")
            .withRequiredArg()
            .ofType(Integer.class)
            .defaultsTo(10_000_000);
        systemThreadOption = parser.accepts("async-profiler-system-threads", "Whether to show system threads like GC and JIT compiler.")
            .availableIf("profile")
            .withRequiredArg()
            .ofType(Boolean.class)
            .defaultsTo(false);
    }

    @Override
    public Profiler withConfig(OptionSet parsedOptions) {
        File profilerHome = getProfilerHome(parsedOptions);
        String event = eventOption.value(parsedOptions);
        Counter counter = counterOption.value(parsedOptions);
        int interval = intervalOption.value(parsedOptions);
        int stackDepth = stackDepthOption.value(parsedOptions);
        int frameBuffer = frameBufferOption.value(parsedOptions);
        Boolean showSystemThreads = systemThreadOption.value(parsedOptions);
        AsyncProfilerConfig config = new AsyncProfilerConfig(profilerHome, event, counter, interval, stackDepth, frameBuffer, showSystemThreads);
        return new AsyncProfiler(config);
    }

    private File getProfilerHome(OptionSet parsedOptions) {
        File profilerHome = profilerHomeOption.value(parsedOptions);
        if (profilerHome == null) {
            String homePath = System.getenv(ASYNC_PROFILER_HOME);
            profilerHome = homePath != null ? new File(homePath) : null;
        }
        if (profilerHome == null) {
            throw new IllegalStateException("No --async-profiler home argument given and " + ASYNC_PROFILER_HOME + " is not set.");
        }
        if (!profilerHome.isDirectory()) {
            throw new IllegalStateException(ASYNC_PROFILER_HOME + " is not a directory.");
        }
        return profilerHome;
    }

    @Override
    protected JvmArgsCalculator jvmArgsWithInstrumentation(ScenarioSettings settings, boolean startRecordingOnProcessStart, boolean captureSnapshotOnProcessExit) {
        if (!startRecordingOnProcessStart && !captureSnapshotOnProcessExit) {
            // Can attach later instead
            return JvmArgsCalculator.DEFAULT;
        }
        return new AsyncProfilerJvmArgsCalculator(config, settings, captureSnapshotOnProcessExit);
    }

    @Override
    protected ProfilerController doNewController(String pid, ScenarioSettings settings) {
        return new AsyncProfilerController(config, pid, settings);
    }

    @Override
    public List<String> summarizeResultFile(File resultFile) {
        if (resultFile.getName().endsWith(".svg")) {
            return Collections.singletonList(resultFile.getAbsolutePath());
        }
        return null;
    }

    @Override
    public String toString() {
        return "async profiler";
    }

    static File stacksFileFor(GradleScenarioDefinition scenario) {
        return new File(scenario.getOutputDir(), scenario.getProfileName() + ".stacks.txt");
    }

    private static class CounterConverter implements ValueConverter<Counter> {
        @Override
        public Counter convert(String value) {
            return Counter.valueOf(value.toUpperCase(Locale.ROOT));
        }

        @Override
        public Class<? extends Counter> valueType() {
            return Counter.class;
        }

        @Override
        public String valuePattern() {
            return null;
        }
    }
}
