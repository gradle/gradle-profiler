package org.gradle.profiler.jfr;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.gradle.profiler.JvmArgsCalculator;
import org.gradle.profiler.Profiler;
import org.gradle.profiler.ProfilerController;
import org.gradle.profiler.ScenarioSettings;

import java.io.File;

public class JfrProfiler extends Profiler {
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
    public ProfilerController newController(final String pid, final ScenarioSettings settings) {
        JFRArgs jfrArgs = (JFRArgs) settings.getInvocationSettings().getProfilerOptions();
        return new JFRControl(jfrArgs, pid, settings.getScenario().getOutputDir());
    }

    @Override
    public JvmArgsCalculator newJvmArgsCalculator(ScenarioSettings settings) {
        return new JFRJvmArgsCalculator();
    }

    @Override
    public void addOptions( final OptionParser parser )
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
}
