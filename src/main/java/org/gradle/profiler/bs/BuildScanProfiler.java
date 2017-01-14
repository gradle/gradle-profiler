package org.gradle.profiler.bs;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.gradle.profiler.GradleArgsCalculator;
import org.gradle.profiler.Profiler;
import org.gradle.profiler.ScenarioSettings;

public class BuildScanProfiler extends Profiler {
    private final String buildScanVersion;

    public BuildScanProfiler() {
        this(null);
    }

    private BuildScanProfiler(String buildScanVersion) {
        this.buildScanVersion = buildScanVersion;
    }

    @Override
    public String toString() {
        return "buildscan";
    }

    @Override
    public GradleArgsCalculator newInstrumentedBuildsGradleArgsCalculator(ScenarioSettings settings) {
        return new BuildScanGradleArgsCalculator(buildScanVersion);
    }

    @Override
    public Profiler withConfig(OptionSet parsedOptions) {
        return new BuildScanProfiler((String) parsedOptions.valueOf("buildscan-version"));
    }

    @Override
    public void addOptions(final OptionParser parser) {
        parser.accepts("buildscan-version", "Version of the Build Scan plugin")
                .availableIf("profile")
                .withOptionalArg();
    }
}
