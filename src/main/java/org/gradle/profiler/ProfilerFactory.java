package org.gradle.profiler;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.gradle.profiler.asyncprofiler.AsyncProfilerFactory;
import org.gradle.profiler.asyncprofiler.AsyncProfilerHeapAllocationProfilerFactory;
import org.gradle.profiler.buildscan.BuildScanProfilerFactory;
import org.gradle.profiler.chrometrace.ChromeTraceProfilerFactory;
import org.gradle.profiler.jfr.JfrProfilerFactory;
import org.gradle.profiler.jprofiler.JProfilerProfilerFactory;
import org.gradle.profiler.yourkit.YourKitHeapAllocationProfilerFactory;
import org.gradle.profiler.yourkit.YourKitSamplingProfilerFactory;
import org.gradle.profiler.yourkit.YourKitTracingProfilerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents some profiling strategy. Produces {@link Profiler} instances from a set of command-line options.
 */
public abstract class ProfilerFactory {
    public static final ProfilerFactory NONE = new ProfilerFactory() {
        @Override
        public Profiler createFromOptions(OptionSet parsedOptions) {
            return Profiler.NONE;
        }
    };

    private final static Map<String, ProfilerFactory> AVAILABLE_PROFILERS = Collections.unmodifiableMap(
        new LinkedHashMap<String, ProfilerFactory>() {{
            put("buildscan", new BuildScanProfilerFactory());
            put("jfr", new JfrProfilerFactory());
            put("jprofiler", new JProfilerProfilerFactory());
            put("yourkit", new YourKitSamplingProfilerFactory());
            put("yourkit-tracing", new YourKitTracingProfilerFactory());
            put("yourkit-heap", new YourKitHeapAllocationProfilerFactory());
            put("async-profiler", AsyncProfilerFactory.INSTANCE);
            put("async-profiler-heap", AsyncProfilerHeapAllocationProfilerFactory.INSTANCE);
            put("chrome-trace", new ChromeTraceProfilerFactory());
        }}
    );

    public static Set<String> getAvailableProfilers() {
        return AVAILABLE_PROFILERS.keySet();
    }

    public static void configureParser(OptionParser parser) {
        for (ProfilerFactory profiler : AVAILABLE_PROFILERS.values()) {
            profiler.addOptions(parser);
        }
    }

    private static ProfilerFactory of(String name) {
        ProfilerFactory profiler = AVAILABLE_PROFILERS.get(name.toLowerCase());
        if (profiler == null) {
            throw new IllegalArgumentException("Unknown profiler : " + name);
        }
        return profiler;
    }

    public static ProfilerFactory of(List<String> profilersList) {
        if (profilersList.size() == 1) {
            String first = profilersList.get(0);
            return of(first);
        }
        return new CompositeProfilerFactory(profilersList.stream().map(ProfilerFactory::of).collect(Collectors.toList()));
    }

    /**
     * Creates a profiler from the given options.
     */
    public abstract Profiler createFromOptions(OptionSet parsedOptions);

    public void addOptions(OptionParser parser) {
    }
}
