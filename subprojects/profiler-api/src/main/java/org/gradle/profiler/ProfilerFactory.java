package org.gradle.profiler;

import joptsimple.OptionParser;
import joptsimple.OptionSet;

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

        @Override
        public String getName() {
            return "none";
        }
    };

    private final static Map<String, ProfilerFactory> AVAILABLE_PROFILERS;

    static {
        ServiceLoader<ProfilerFactory> factories = ServiceLoader.load(ProfilerFactory.class);
        Map<String, ProfilerFactory> factoriesByName = new LinkedHashMap<>();
        factories.forEach(factory -> {
            factoriesByName.put(factory.getName(), factory);
        });
        AVAILABLE_PROFILERS = Collections.unmodifiableMap(factoriesByName);
    }

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

    public abstract String getName();
}
