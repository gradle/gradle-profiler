package org.gradle.profiler.jprofiler;

import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpecBuilder;
import org.gradle.profiler.*;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class JProfilerProfiler extends Profiler {
    private final JProfilerConfig jProfilerConfig;
    private ArgumentAcceptingOptionSpec<String> homeDir;
    private ArgumentAcceptingOptionSpec<String> configOption;
    private ArgumentAcceptingOptionSpec<String> sessionIdOption;
    private ArgumentAcceptingOptionSpec<String> configFileOption;
    private OptionSpecBuilder allocOption;
    private OptionSpecBuilder monitorsOption;
    private OptionSpecBuilder heapDumpOption;
    private ArgumentAcceptingOptionSpec<String> probesOption;

    public JProfilerProfiler() {
        this(null);
    }

    private JProfilerProfiler(JProfilerConfig jProfilerConfig) {
        this.jProfilerConfig = jProfilerConfig;
    }

    @Override
    public String toString() {
        return "JProfiler";
    }

    @Override
    public List<String> summarizeResultFile(File resultFile) {
        if (resultFile.getName().endsWith(".jps")) {
            return Collections.singletonList(resultFile.getAbsolutePath());
        }
        return null;
    }

    @Override
    public ProfilerController newController(String pid, ScenarioSettings settings) {
        if (settings.getInvocationSettings().getInvoker() == Invoker.NoDaemon) {
            return ProfilerController.EMPTY;
        } else {
            return new JProfilerController(settings, jProfilerConfig);
        }
    }

    @Override
    public JvmArgsCalculator newJvmArgsCalculator(ScenarioSettings settings) {
        return new JProfilerJvmArgsCalculator(jProfilerConfig, settings);
    }

    @Override
    public void addOptions(OptionParser parser) {
        homeDir = parser.accepts("jprofiler-home", "JProfiler installation directory").availableIf("profile")
                .withOptionalArg().ofType(String.class).defaultsTo(JProfiler.getDefaultHomeDir());
        configOption = parser.accepts("jprofiler-config", "JProfiler built-in configuration name (sampling|sampling-all|instrumentation)")
                .availableIf("profile").withOptionalArg().ofType(String.class).defaultsTo("sampling");
        sessionIdOption = parser.accepts("jprofiler-session-id", "Use session with this id from the JProfiler installation instead of using the built-in config")
                .availableUnless("jprofiler-config").withRequiredArg().ofType(String.class);
        configFileOption = parser.accepts("jprofiler-config-file", "Use another config file for --jprofiler-session-id instead of the global config file")
                .availableIf("jprofiler-session-id").withRequiredArg().ofType(String.class);
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
    public Profiler withConfig(OptionSet parsedOptions) {
        return new JProfilerProfiler(newConfigObject(parsedOptions));
    }

    private JProfilerConfig newConfigObject(OptionSet parsedOptions) {
        return new JProfilerConfig(
                parsedOptions.valueOf(homeDir),
                parsedOptions.valueOf(configOption),
                parsedOptions.valueOf(sessionIdOption),
                parsedOptions.valueOf(configFileOption),
                parsedOptions.has(allocOption),
                parsedOptions.has(monitorsOption),
                parsedOptions.has(heapDumpOption),
                parsedOptions.valuesOf(probesOption));
    }
}
