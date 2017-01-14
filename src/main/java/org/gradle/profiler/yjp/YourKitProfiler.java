package org.gradle.profiler.yjp;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.gradle.profiler.*;

public class YourKitProfiler extends Profiler {
    @Override
    public String toString() {
        return "YourKit";
    }

    @Override
    public ProfilerController newController(String pid, ScenarioSettings settings) {
        if (settings.getScenario().getInvoker() == Invoker.NoDaemon) {
            return ProfilerController.EMPTY;
        }
        return new YourKitProfilerController((YourKitConfig) settings.getInvocationSettings().getProfilerOptions());
    }

    @Override
    public JvmArgsCalculator newJvmArgsCalculator(ScenarioSettings settings) {
        if (settings.getScenario().getInvoker() == Invoker.NoDaemon) {
            return JvmArgsCalculator.DEFAULT;
        }
        return new YourKitJvmArgsCalculator(settings, false);
    }

    @Override
    public JvmArgsCalculator newInstrumentedBuildsJvmArgsCalculator(ScenarioSettings settings) {
        if (settings.getScenario().getInvoker() == Invoker.NoDaemon) {
            return new YourKitJvmArgsCalculator(settings, true);
        }
        return JvmArgsCalculator.DEFAULT;
    }

    @Override
    public void addOptions(OptionParser parser) {
        parser.accepts("yourkit-memory", "Capture memory snapshot").availableIf("yourkit");
    }

    @Override
    public Object newConfigObject(OptionSet parsedOptions) {
        return new YourKitConfig(parsedOptions.has("yourkit-memory"));
    }
}
