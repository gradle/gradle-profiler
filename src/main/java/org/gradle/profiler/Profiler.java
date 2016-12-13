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
import org.gradle.profiler.bs.BuildScanController;
import org.gradle.profiler.hp.HonestProfilerArgs;
import org.gradle.profiler.hp.HonestProfilerControl;
import org.gradle.profiler.hp.HonestProfilerJvmArgsCalculator;
import org.gradle.profiler.jfr.JFRArgs;
import org.gradle.profiler.jfr.JFRControl;
import org.gradle.profiler.jfr.JFRJvmArgsCalculator;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Profiler {

    public static final Profiler NONE = new Profiler();
    public static final Profiler JFR = new Profiler() {
        @Override
        public ProfilerController newController(final String pid, final InvocationSettings settings, final BuildInvoker invoker) {
            JFRArgs jfrArgs = new JFRArgs(pid, new File(settings.getOutputDir(), "profile.jfr"));
            return new JFRControl(jfrArgs);
        }

        @Override
        public JvmArgsCalculator newJvmArgsCalculator(InvocationSettings settings) {
            return new JFRJvmArgsCalculator();
        }
    };
    public static final Profiler HP = new Profiler() {
        @Override
        public Object newConfigObject(final OptionSet parsedOptions) {
            File tmpLog = new File(System.getProperty("java.io.tmpdir"), "hp.log");
            int i = 0;
            while (tmpLog.exists()) {
                tmpLog = new File(System.getProperty("java.io.tmpdir"), "hp.log."+(++i));
            }
            HonestProfilerArgs args = new HonestProfilerArgs(
                    new File((String) parsedOptions.valueOf("hp-home")),
                    tmpLog,
                    Integer.valueOf((String) parsedOptions.valueOf("hp-port")),
                    Integer.valueOf((String) parsedOptions.valueOf("hp-interval"))
            );
            return args;
        }

        @Override
        public ProfilerController newController(final String pid, final InvocationSettings settings, final BuildInvoker invoker) {
            HonestProfilerArgs args = (HonestProfilerArgs) settings.getProfilerOptions();
            return new HonestProfilerControl(args, settings.getOutputDir());
        }

        @Override
        public JvmArgsCalculator newJvmArgsCalculator(InvocationSettings settings) {
            return new HonestProfilerJvmArgsCalculator((HonestProfilerArgs) settings.getProfilerOptions());
        }

        @Override
        public void addOptions(final OptionParser parser) {
            parser.accepts("hp-port", "Honest Profiler port")
                    .availableIf("profile")
                    .withOptionalArg()
                    .defaultsTo("18000");
            parser.accepts("hp-home", "Honest Profiler home directory")
                    .availableIf("profile")
                    .withOptionalArg()
                    .defaultsTo(System.getenv().getOrDefault("HP_HOME_DIR", ""));
            parser.accepts("hp-interval", "Honest Profiler sampling interval")
                    .availableIf("profile")
                    .withOptionalArg()
                    .defaultsTo("7");
        }
    };
    public static final Profiler BUILDSCAN = new Profiler() {
        @Override
        public ProfilerController newController(final String pid, final InvocationSettings settings, final BuildInvoker invoker) {
            try {
                return new BuildScanController(invoker, (String) settings.getProfilerOptions());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Object newConfigObject(final OptionSet parsedOptions) {
            return parsedOptions.valueOf("buildscan-version");
        }

        @Override
        void addOptions(final OptionParser parser) {
            parser.accepts("buildscan-version", "Version of the Build Scan plugin")
                    .availableIf("profile")
                    .withOptionalArg();
        }
    };

    private final static Map<String, Profiler> AVAILABLE_PROFILERS = Collections.unmodifiableMap(
            new LinkedHashMap<String, Profiler>() {{
                put("jfr", JFR);
                put("hp", HP);
                put("buildscan", BUILDSCAN);
            }}
    );

    public ProfilerController newController(final String pid, final InvocationSettings settings, final BuildInvoker invoker) {
        return ProfilerController.EMPTY;
    }

    public JvmArgsCalculator newJvmArgsCalculator(final InvocationSettings settings) {
        return JvmArgsCalculator.DEFAULT;
    }

    public Object newConfigObject(OptionSet parsedOptions) {
        return null;
    }

    void addOptions(OptionParser parser) {

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

    public static Profiler of(final List<String> profilersList) {
        if (profilersList.size() == 1) {
            String first = profilersList.get(0);
            return of(first);
        }
        throw new UnsupportedOperationException("Multiple profilers are not supported");
    }
}
