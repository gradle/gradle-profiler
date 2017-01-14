package org.gradle.profiler.bs;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.gradle.profiler.GradleArgsCalculator;
import org.gradle.profiler.Profiler;
import org.gradle.profiler.ScenarioSettings;

public class BuildScanProfiler extends Profiler {
    @Override
    public String toString() {
        return "buildscan";
    }

    @Override
    public GradleArgsCalculator newInstrumentedBuildsGradleArgsCalculator(ScenarioSettings settings) {
        return new BuildScanGradleArgsCalculator(settings);
    }

    @Override
    public Object newConfigObject(final OptionSet parsedOptions) {
        return parsedOptions.valueOf("buildscan-version");
    }

    @Override
    public void addOptions(final OptionParser parser) {
        parser.accepts("buildscan-version", "Version of the Build Scan plugin")
                .availableIf("profile")
                .withOptionalArg();
    }
}
