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

import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpecBuilder;
import org.gradle.profiler.bs.BuildScanController;
import org.gradle.profiler.ct.ChromeTraceController;
import org.gradle.profiler.hp.HonestProfilerArgs;
import org.gradle.profiler.hp.HonestProfilerControl;
import org.gradle.profiler.hp.HonestProfilerJvmArgsCalculator;
import org.gradle.profiler.jfr.JFRArgs;
import org.gradle.profiler.jfr.JFRControl;
import org.gradle.profiler.jfr.JFRJvmArgsCalculator;
import org.gradle.profiler.jprofiler.JProfiler;
import org.gradle.profiler.jprofiler.JProfilerConfig;
import org.gradle.profiler.jprofiler.JProfilerController;
import org.gradle.profiler.jprofiler.JProfilerJvmArgsCalculator;
import org.gradle.profiler.yjp.YourKitConfig;
import org.gradle.profiler.yjp.YourKitJvmArgsCalculator;
import org.gradle.profiler.yjp.YourKitProfilerController;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class Profiler {

    public static final Profiler NONE = new Profiler() {
        @Override
        public String toString() {
            return "none";
        }
    };
    public static final Profiler JFR = new Profiler() {
        @Override
        public String toString() {
            return "JFR";
        }

        @Override
        public Object newConfigObject( OptionSet parsedOptions ) {
            return new JFRArgs(
                new File((String) parsedOptions.valueOf( "jfr-fg-home" )),
                new File((String) parsedOptions.valueOf( "fg-home" ))
            );
        }

        @Override
        public ProfilerController newController(final String pid, final ScenarioSettings settings, final BuildInvoker invoker) {
            JFRArgs jfrArgs = (JFRArgs) settings.getInvocationSettings().getProfilerOptions();
            return new JFRControl(jfrArgs, pid, settings.getScenario().getOutputDir());
        }

        @Override
        public JvmArgsCalculator newJvmArgsCalculator(ScenarioSettings settings) {
            return new JFRJvmArgsCalculator();
        }

        @Override
        void addOptions( final OptionParser parser )
        {
            parser.accepts("jfr-fg-home", "JFR FlameGraph home directory - https://github.com/chrishantha/jfr-flame-graph")
                  .availableIf("profile")
                  .withOptionalArg()
                  .defaultsTo(System.getenv().getOrDefault("JFR_FG_HOME_DIR", ""));
            parser.accepts("fg-home", "FlameGraph home directory")
                  .availableIf("profile")
                  .withOptionalArg()
                  .defaultsTo(System.getenv().getOrDefault("FG_HOME_DIR", ""));
        }
    };
    public static final Profiler HP = new Profiler() {
        @Override
        public String toString() {
            return "Honest profiler";
        }

        @Override
        public Object newConfigObject(final OptionSet parsedOptions) {
            File tmpLog = new File(System.getProperty("java.io.tmpdir"), "hp.log");
            int i = 0;
            while (tmpLog.exists()) {
                tmpLog = new File(System.getProperty("java.io.tmpdir"), "hp.log."+(++i));
            }
            HonestProfilerArgs args = new HonestProfilerArgs(
                    new File((String) parsedOptions.valueOf("hp-home")),
                    new File((String) parsedOptions.valueOf("fg-home")),
                    tmpLog,
                    Integer.valueOf((String) parsedOptions.valueOf("hp-port")),
                    Integer.valueOf((String) parsedOptions.valueOf("hp-interval")),
                    Integer.valueOf((String) parsedOptions.valueOf("hp-max-frames")));
            return args;
        }

        @Override
        public ProfilerController newController(final String pid, final ScenarioSettings settings, final BuildInvoker invoker) {
            HonestProfilerArgs args = (HonestProfilerArgs) settings.getInvocationSettings().getProfilerOptions();
            return new HonestProfilerControl(args, settings.getScenario().getOutputDir());
        }

        @Override
        public JvmArgsCalculator newJvmArgsCalculator(ScenarioSettings settings) {
            return new HonestProfilerJvmArgsCalculator((HonestProfilerArgs) settings.getInvocationSettings().getProfilerOptions());
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
            parser.accepts("fg-home", "FlameGraph home directory")
                    .availableIf("profile")
                    .withOptionalArg()
                    .defaultsTo(System.getenv().getOrDefault("FG_HOME_DIR", ""));
            parser.accepts("hp-interval", "Honest Profiler sampling interval")
                    .availableIf("profile")
                    .withOptionalArg()
                    .defaultsTo("7");
            parser.accepts("hp-max-frames", "Honest Profiler max stack frame height")
                    .availableIf("profile")
                    .withOptionalArg()
                    .defaultsTo("1024");
        }
    };
    public static final Profiler BUILDSCAN = new Profiler() {
        @Override
        public String toString() {
            return "buildscan";
        }

        @Override
        public ProfilerController newController(final String pid, final ScenarioSettings settings, final BuildInvoker invoker) {
            try {
                return new BuildScanController(invoker, (String) settings.getInvocationSettings().getProfilerOptions());
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
    public static final Profiler YOUR_KIT = new Profiler() {
        @Override
        public String toString() {
            return "YourKit";
        }

        @Override
        public ProfilerController newController(String pid, ScenarioSettings settings, BuildInvoker invoker) {
            return new YourKitProfilerController((YourKitConfig) settings.getInvocationSettings().getProfilerOptions());
        }

        @Override
        public JvmArgsCalculator newJvmArgsCalculator(ScenarioSettings settings) {
            return new YourKitJvmArgsCalculator(settings);
        }

        @Override
        void addOptions(OptionParser parser) {
            parser.accepts("yourkit-memory", "Capture memory snapshot").availableIf("yourkit");
        }

        @Override
        public Object newConfigObject(OptionSet parsedOptions) {
            return new YourKitConfig(parsedOptions.has("yourkit-memory"));
        }
    };

    public static final Profiler JPROFILER = new Profiler() {

        private ArgumentAcceptingOptionSpec<String> homeDir;
        private ArgumentAcceptingOptionSpec<String> configOption;
        private ArgumentAcceptingOptionSpec<String> sessionIdOption;
        private OptionSpecBuilder allocOption;
        private OptionSpecBuilder monitorsOption;
        private OptionSpecBuilder heapDumpOption;
        private ArgumentAcceptingOptionSpec<String> probesOption;

        @Override
        public ProfilerController newController(String pid, ScenarioSettings settings, BuildInvoker invoker) {
            return new JProfilerController(settings);
        }

        @Override
        public JvmArgsCalculator newJvmArgsCalculator(ScenarioSettings settings) {
            return new JProfilerJvmArgsCalculator(settings);
        }

        @Override
        void addOptions(OptionParser parser) {
            homeDir = parser.accepts("jprofiler-home", "JProfiler installation directory").availableIf("profile")
                    .withOptionalArg().ofType(String.class).defaultsTo(JProfiler.getDefaultHomeDir());
            configOption = parser.accepts("jprofiler-config", "JProfiler built-in configuration name (sampling|instrumentation)")
                    .availableIf("profile").withOptionalArg().ofType(String.class).defaultsTo("sampling");
            sessionIdOption = parser.accepts("jprofiler-session-id", "Use session with this id from the JProfiler installation instead of using the built-in config")
                    .availableUnless("jprofiler-config").withOptionalArg().ofType(String.class);
            allocOption = parser.accepts("jprofiler-alloc", "Record allocations")
                    .availableIf("profile");
            monitorsOption = parser.accepts("jprofiler-monitors", "Record monitor usage")
                    .availableIf("profile");
            heapDumpOption = parser.accepts("jprofiler-heapdump", "Trigger heap dump after a build")
                    .availableIf("profile");
            probesOption = parser.accepts("jprofiler-probes", "Record probes (builtin.FileProbe|builtin.SocketProbe|builtin.ProcessProbe|builtin.ClassLoaderProbe|builtin.ExceptionProbe, see Controller javadoc for the full list) separated by commas, add :+events to probe name to enable event recording")
                    .availableIf("profile").withRequiredArg().ofType(String.class).withValuesSeparatedBy(',').defaultsTo(new String[0]);
        }

        @Override
        public Object newConfigObject(OptionSet parsedOptions) {
            return new JProfilerConfig(
                    parsedOptions.valueOf(homeDir),
                    parsedOptions.valueOf(configOption),
                    parsedOptions.valueOf(sessionIdOption),
                    parsedOptions.has(allocOption),
                    parsedOptions.has(monitorsOption),
                    parsedOptions.has(heapDumpOption),
                    parsedOptions.valuesOf(probesOption));
        }

    };

    public static final Profiler CHROME_TRACE = new Profiler() {
        @Override
        public String toString() {
            return "chrome-trace";
        }

        @Override
        public ProfilerController newController(final String pid, final ScenarioSettings settings, final BuildInvoker invoker) {
            try {
                return new ChromeTraceController(invoker, settings.getScenario().getOutputDir());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    };

    private final static Map<String, Profiler> AVAILABLE_PROFILERS = Collections.unmodifiableMap(
            new LinkedHashMap<String, Profiler>() {{
                put("jfr", JFR);
                put("hp", HP);
                put("buildscan", BUILDSCAN);
                put("chrome-trace", CHROME_TRACE);
                put("yourkit", YOUR_KIT);
                put("jprofiler", JPROFILER);
            }}
    );

    public ProfilerController newController(final String pid, final ScenarioSettings settings, final BuildInvoker invoker) {
        return ProfilerController.EMPTY;
    }

    public JvmArgsCalculator newJvmArgsCalculator(final ScenarioSettings settings) {
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
        return new CompositeProfiler(profilersList.stream().map(Profiler::of).collect(Collectors.toList()));
    }

    private static class CompositeProfiler extends Profiler {
        private final List<Profiler> delegates;
        private final Map<Profiler, Object> profilerOptions = new HashMap<>();

        private CompositeProfiler(final List<Profiler> delegates) {
            this.delegates = delegates;
        }

        @Override
        public String toString() {
            return delegates.stream().map(Object::toString).collect(Collectors.joining(", "));
        }

        @Override
        public ProfilerController newController(final String pid, final ScenarioSettings settings, final BuildInvoker invoker) {
            List<ProfilerController> controllers = delegates.stream()
                    .map((Profiler prof) -> prof.newController(pid,
                            settingsFor(prof, settings), invoker))
                    .collect(Collectors.toList());
            return new ProfilerController() {
                @Override
                public void start() throws IOException, InterruptedException {
                    for (ProfilerController controller : controllers) {
                        controller.start();
                    }
                }

                @Override
                public void stop() throws IOException, InterruptedException {
                    for (ProfilerController controller : controllers) {
                        controller.stop();
                    }
                }
            };
        }

        private ScenarioSettings settingsFor(final Profiler prof, final ScenarioSettings scenarioSettings) {
            InvocationSettings settings = scenarioSettings.getInvocationSettings();
            InvocationSettings newSettings = new InvocationSettings(settings.getProjectDir(), prof, profilerOptions.get(prof), settings.isBenchmark(), settings.getOutputDir(), settings.getInvoker(), settings.isDryRun(), settings.getScenarioFile(), settings.getVersions(), settings.getTargets(), settings.getSystemProperties(), settings.getGradleUserHome(), settings.isBuck());
            return new ScenarioSettings(newSettings, scenarioSettings.getScenario());
        }

        @Override
        public JvmArgsCalculator newJvmArgsCalculator(final ScenarioSettings settings) {
            return new JvmArgsCalculator() {
                @Override
                public void calculateJvmArgs(final List<String> jvmArgs) {
                    delegates.forEach(prof -> prof.newJvmArgsCalculator(settingsFor(prof, settings)).calculateJvmArgs(jvmArgs));
                }
            };
        }

        @Override
        public Object newConfigObject(final OptionSet parsedOptions) {
            for (Profiler delegate : delegates) {
                profilerOptions.put(delegate, delegate.newConfigObject(parsedOptions));
            }
            return Collections.unmodifiableMap(profilerOptions);
        }

    }
}
