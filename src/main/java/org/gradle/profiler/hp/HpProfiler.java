package org.gradle.profiler.hp;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.gradle.profiler.JvmArgsCalculator;
import org.gradle.profiler.Profiler;
import org.gradle.profiler.ProfilerController;
import org.gradle.profiler.ScenarioSettings;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class HpProfiler extends Profiler {
    private final HonestProfilerArgs honestProfilerArgs;

    public HpProfiler() {
        this(null);
    }

    private HpProfiler(HonestProfilerArgs honestProfilerArgs) {
        this.honestProfilerArgs = honestProfilerArgs;
    }

    @Override
    public String toString() {
        return "Honest profiler";
    }

    @Override
    public List<String> summarizeResultFile(File resultFile) {
        if (resultFile.getName().endsWith(".svg")) {
            return Collections.singletonList(resultFile.getAbsolutePath());
        }
        return null;
    }

    @Override
    public Profiler withConfig(OptionSet parsedOptions) {
        return new HpProfiler(newConfigObject(parsedOptions));
    }

    private HonestProfilerArgs newConfigObject(final OptionSet parsedOptions) {
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
    public ProfilerController newController(final String pid, final ScenarioSettings settings) {
        return new HonestProfilerControl(honestProfilerArgs, settings);
    }

    @Override
    public JvmArgsCalculator newJvmArgsCalculator(ScenarioSettings settings) {
        return new HonestProfilerJvmArgsCalculator(honestProfilerArgs);
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
}
