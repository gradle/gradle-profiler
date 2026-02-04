package org.gradle.profiler.heapdump;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.gradle.profiler.Profiler;
import org.gradle.profiler.ProfilerFactory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class HeapDumpProfilerFactory extends ProfilerFactory {
    private static final String HEAP_DUMP_WHEN_OPTION = "heap-dump-when";
    private static final Set<String> ALLOWED_VALUES = new HashSet<>(Arrays.asList("config-end", "build-end"));
    private static final String DEFAULT_VALUE = "build-end";

    @Override
    public void addOptions(OptionParser parser) {
        parser.accepts(HEAP_DUMP_WHEN_OPTION,
            "When to capture heap dumps. Comma-separated list of: config-end, build-end. Default: build-end")
            .withRequiredArg()
            .ofType(String.class)
            .defaultsTo(DEFAULT_VALUE);
    }

    @Override
    public Profiler createFromOptions(OptionSet parsedOptions) {
        String heapDumpWhenValue = (String) parsedOptions.valueOf(HEAP_DUMP_WHEN_OPTION);

        // Parse comma-separated values
        Set<String> strategies = Arrays.stream(heapDumpWhenValue.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toSet());

        // Validate each strategy
        for (String strategy : strategies) {
            if (!ALLOWED_VALUES.contains(strategy)) {
                throw new IllegalArgumentException(
                    "Invalid --heap-dump-when value: '" + strategy + "'. " +
                    "Allowed values: " + String.join(", ", ALLOWED_VALUES)
                );
            }
        }

        if (strategies.isEmpty()) {
            strategies = new HashSet<>(Arrays.asList(DEFAULT_VALUE));
        }

        return new HeapDumpProfiler(strategies);
    }

    @Override
    public String getName() {
        return "heap-dump";
    }
}
