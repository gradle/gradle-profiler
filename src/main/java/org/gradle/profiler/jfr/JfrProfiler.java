package org.gradle.profiler.jfr;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.gradle.profiler.JvmArgsCalculator;
import org.gradle.profiler.Profiler;
import org.gradle.profiler.ProfilerController;
import org.gradle.profiler.ScenarioSettings;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class JfrProfiler extends Profiler {
    private final JFRArgs jfrArgs;

    public JfrProfiler() {
        this(null);
    }

    private JfrProfiler(JFRArgs jfrArgs) {
        this.jfrArgs = jfrArgs;
    }

    @Override
    public String toString() {
        return "JFR";
    }

    @Override
    public List<String> summarizeResultFile(File resultFile) {
        if (resultFile.getName().endsWith(".jfr")) {
            return Collections.singletonList(resultFile.getAbsolutePath());
        }
        return null;
    }

    @Override
    public Profiler withConfig(OptionSet parsedOptions) {
        return new JfrProfiler(newConfigObject(parsedOptions));
    }

    private JFRArgs newConfigObject(OptionSet parsedOptions ) {
        String jfrSettings = (String) parsedOptions.valueOf("jfr-settings");
        if (jfrSettings.endsWith(".jfc")) {
            jfrSettings = new File(jfrSettings).getAbsolutePath();
        }
        return new JFRArgs(
            new File((String) parsedOptions.valueOf( "jfr-fg-home" )),
            new File((String) parsedOptions.valueOf( "fg-home" )),
            jfrSettings
        );
    }

    @Override
    public ProfilerController newController(final String pid, final ScenarioSettings settings) {
        return new JFRControl(jfrArgs, pid, settings);
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
        parser.accepts("jfr-settings", "JFR settings - Either a .jfc file or the name of a template known to your JFR installation")
                .availableIf("profile")
                .withOptionalArg()
                .defaultsTo("profile");
    }
}
