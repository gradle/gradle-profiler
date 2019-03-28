/*
 * Copyright 2003-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.profiler;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.gradle.profiler.asyncprofiler.AsyncProfiler;
import org.gradle.profiler.buildscan.BuildScanProfiler;
import org.gradle.profiler.chrometrace.ChromeTraceProfiler;
import org.gradle.profiler.jfr.JfrProfiler;
import org.gradle.profiler.jprofiler.JProfilerProfiler;
import org.gradle.profiler.yourkit.YourKitProfiler;

import java.io.File;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class Profiler {

    public static final Profiler NONE = new Profiler() {
        @Override
        public String toString() {
            return "none";
        }
    };

    private final static Map<String, Profiler> AVAILABLE_PROFILERS = Collections.unmodifiableMap(
            new LinkedHashMap<String, Profiler>() {{
                put("buildscan", new BuildScanProfiler());
                put("jfr", new JfrProfiler());
                put("jprofiler", new JProfilerProfiler());
                put("yourkit", new YourKitProfiler());
                put("async-profiler", new AsyncProfiler());
                put("chrome-trace", new ChromeTraceProfiler());
            }}
    );

    public ProfilerController newController(String pid, ScenarioSettings settings) {
        return ProfilerController.EMPTY;
    }

    /**
     * Returns a calculator that provides JVM args that should be applied to all builds, including warm-up builds.
     */
    public JvmArgsCalculator newJvmArgsCalculator(ScenarioSettings settings) {
        return JvmArgsCalculator.DEFAULT;
    }

    /**
     * Returns a calculator that provides JVM args that should be applied to instrumented builds, but not warm-up builds.
     */
    public JvmArgsCalculator newInstrumentedBuildsJvmArgsCalculator(ScenarioSettings settings) {
        return JvmArgsCalculator.DEFAULT;
    }

    /**
     * Returns a calculator that provides Gradle args that should be applied to all builds, including warm-up builds.
     */
    public GradleArgsCalculator newGradleArgsCalculator(ScenarioSettings settings) {
        return GradleArgsCalculator.DEFAULT;
    }

    /**
     * Returns a calculator that provides Gradle args that should be applied to instrumented builds, but not warm-up builds.
     */
    public GradleArgsCalculator newInstrumentedBuildsGradleArgsCalculator(ScenarioSettings settings) {
        return GradleArgsCalculator.DEFAULT;
    }

    public Profiler withConfig(OptionSet parsedOptions) {
        return this;
    }

    public void addOptions(OptionParser parser) {
    }

    /**
     * Describe the given file, if recognized and should be reported to the user.
     */
    public void summarizeResultFile(File resultFile, Consumer<String> consumer) {
    }

    public static Set<String> getAvailableProfilers() {
        return AVAILABLE_PROFILERS.keySet();
    }

    static void configureParser(OptionParser parser) {
        for (Profiler profiler : AVAILABLE_PROFILERS.values()) {
            profiler.addOptions(parser);
        }
    }

    private static Profiler of(String name) {
        Profiler profiler = AVAILABLE_PROFILERS.get(name.toLowerCase());
        if (profiler == null) {
            throw new IllegalArgumentException("Unknown profiler : " + name);
        }
        return profiler;
    }

    public static Profiler of(List<String> profilersList) {
        if (profilersList.size() == 1) {
            String first = profilersList.get(0);
            return of(first);
        }
        return new CompositeProfiler(profilersList.stream().map(Profiler::of).collect(Collectors.toList()));
    }
}
