package org.gradle.profiler.asyncprofiler;

import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.ValueConverter;
import org.gradle.profiler.OperatingSystem;
import org.gradle.profiler.Profiler;
import org.gradle.profiler.ProfilerFactory;

import java.io.File;
import java.util.Locale;

public class AsyncProfilerFactory extends ProfilerFactory {
    static final String ASYNC_PROFILER_HOME = "ASYNC_PROFILER_HOME";
    public static final AsyncProfilerFactory INSTANCE = new AsyncProfilerFactory();

    private ArgumentAcceptingOptionSpec<File> profilerHomeOption;
    private ArgumentAcceptingOptionSpec<String> eventOption;
    private ArgumentAcceptingOptionSpec<AsyncProfilerConfig.Counter> counterOption;
    private ArgumentAcceptingOptionSpec<Integer> frameBufferOption;
    private ArgumentAcceptingOptionSpec<Integer> intervalOption;
    private ArgumentAcceptingOptionSpec<Integer> stackDepthOption;
    private ArgumentAcceptingOptionSpec<Boolean> systemThreadOption;

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
            .defaultsTo(AsyncProfilerConfig.Counter.SAMPLES);
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
    public Profiler createFromOptions(OptionSet parsedOptions) {
        AsyncProfilerConfig config = createConfig(parsedOptions);
        return new AsyncProfiler(config);
    }

    AsyncProfilerConfig createConfig(OptionSet parsedOptions) {
        File profilerHome = getProfilerHome(parsedOptions);
        String event = eventOption.value(parsedOptions);
        AsyncProfilerConfig.Counter counter = counterOption.value(parsedOptions);
        int interval = intervalOption.value(parsedOptions);
        int stackDepth = stackDepthOption.value(parsedOptions);
        int frameBuffer = frameBufferOption.value(parsedOptions);
        Boolean showSystemThreads = systemThreadOption.value(parsedOptions);
        AsyncProfilerConfig config = new AsyncProfilerConfig(profilerHome, event, counter, interval, stackDepth, frameBuffer, showSystemThreads);
        return config;
    }

    private File getProfilerHome(OptionSet parsedOptions) {
        File profilerHome = profilerHomeOption.value(parsedOptions);
        if (profilerHome == null) {
            String homePath = System.getenv(ASYNC_PROFILER_HOME);
            profilerHome = homePath != null ? new File(homePath) : null;
        }
        if (profilerHome != null && !profilerHome.isDirectory()) {
            throw new IllegalStateException(ASYNC_PROFILER_HOME + " is not a directory.");
        }
        if (profilerHome == null) {
            profilerHome = AsyncProfilerDownload.defaultHome();
        }
        if (profilerHome == null) {
            throw new IllegalStateException("Async profiler not supported on " + OperatingSystem.getId());
        }
        return profilerHome;
    }

    private static class CounterConverter implements ValueConverter<AsyncProfilerConfig.Counter> {
        @Override
        public AsyncProfilerConfig.Counter convert(String value) {
            return AsyncProfilerConfig.Counter.valueOf(value.toUpperCase(Locale.ROOT));
        }

        @Override
        public Class<? extends AsyncProfilerConfig.Counter> valueType() {
            return AsyncProfilerConfig.Counter.class;
        }

        @Override
        public String valuePattern() {
            return null;
        }
    }
}
