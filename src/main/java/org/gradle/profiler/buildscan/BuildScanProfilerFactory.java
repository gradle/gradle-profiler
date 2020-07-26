package org.gradle.profiler.buildscan;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.gradle.profiler.Profiler;
import org.gradle.profiler.ProfilerFactory;

public class BuildScanProfilerFactory extends ProfilerFactory {
    @Override
    public Profiler createFromOptions(OptionSet parsedOptions) {
        return new BuildScanProfiler((String) parsedOptions.valueOf("buildscan-version"));
    }

    @Override
    public void addOptions(final OptionParser parser) {
        parser.accepts("buildscan-version", "Version of the Build Scan plugin")
            .availableIf("profile")
            .withOptionalArg();
    }
}
