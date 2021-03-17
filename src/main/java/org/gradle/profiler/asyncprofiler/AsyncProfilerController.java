package org.gradle.profiler.asyncprofiler;

import org.gradle.profiler.CommandExec;
import org.gradle.profiler.GradleScenarioDefinition;
import org.gradle.profiler.InstrumentingProfiler;
import org.gradle.profiler.ScenarioSettings;
import org.gradle.profiler.flamegraph.FlameGraphSanitizer;
import org.gradle.profiler.flamegraph.FlameGraphTool;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.gradle.profiler.asyncprofiler.AsyncProfilerConfig.Counter;
import static org.gradle.profiler.flamegraph.FlameGraphSanitizer.SanitizeFunction;

public class AsyncProfilerController implements InstrumentingProfiler.SnapshotCapturingProfilerController {
    private final AsyncProfilerConfig profilerConfig;
    private final ScenarioSettings scenarioSettings;
    private final FlameGraphTool flamegraphGenerator;
    private final FlameGraphSanitizer rawFlamegraphSanitizer;
    private final FlameGraphSanitizer simplifiedFlamegraphSanitizer;
    private final File stacks;

    public AsyncProfilerController(AsyncProfilerConfig profilerConfig, ScenarioSettings scenarioSettings) {
        this.profilerConfig = profilerConfig;
        this.scenarioSettings = scenarioSettings;
        this.rawFlamegraphSanitizer = FlameGraphSanitizer.raw();
        this.simplifiedFlamegraphSanitizer = profilerConfig.isIncludeSystemThreads()
            ? FlameGraphSanitizer.simplified()
            : FlameGraphSanitizer.simplified(new RemoveSystemThreads());
        this.flamegraphGenerator = new FlameGraphTool();
        this.stacks = AsyncProfiler.stacksFileFor(scenarioSettings.getScenario());
    }

    public String getName() {
        return "async profiler";
    }

    @Override
    public void startRecording(String pid) throws IOException, InterruptedException {
        new CommandExec().run(
            getProfilerScript().getAbsolutePath(),
            "start",
            "-e", profilerConfig.getEvent(),
            "-i", String.valueOf(profilerConfig.getInterval()),
            "-j", String.valueOf(profilerConfig.getStackDepth()),
            "-b", String.valueOf(profilerConfig.getFrameBuffer()),
            pid
        );
    }

    @Override
    public void stopRecording(String pid) throws IOException, InterruptedException {
        new CommandExec().run(
            getProfilerScript().getAbsolutePath(),
            "stop",
            "-o", "collapsed=" + profilerConfig.getCounter().name().toLowerCase(Locale.ROOT),
            "-a",
            "-f", stacks.getAbsolutePath(),
            pid
        );
    }

    @Override
    public void captureSnapshot(String pid) {
    }

    @Override
    public void stopSession() {
        validateStacks();
        GradleScenarioDefinition scenario = scenarioSettings.getScenario();
        if (flamegraphGenerator.checkInstallation()) {
            String outputBaseName = scenario.getProfileName() + "-" + profilerConfig.getEvent();
            generateFlames(scenario.getOutputDir(), outputBaseName + "-raw", rawFlamegraphSanitizer);
            generateFlames(scenario.getOutputDir(), outputBaseName + "-simplified", simplifiedFlamegraphSanitizer);
        }
    }

    private void validateStacks() {
        if (!stacks.isFile() || stacks.length() == 0) {
            throw new RuntimeException("No stacks have been capture by Async profiler. If you are on Linux, you may need to set two runtime variables:\n" +
                "# sysctl kernel.perf_event_paranoid=1\n" +
                "# sysctl kernel.kptr_restrict=0");
        }
    }

    private void generateFlames(File outputDir, String outputBaseName, FlameGraphSanitizer flameGraphSanitizer) {
        File processedStacks = new File(outputDir, outputBaseName + "-stacks.txt");
        flameGraphSanitizer.sanitize(stacks, processedStacks);
        generateGraphs(outputDir, outputBaseName, processedStacks);
    }

    private void generateGraphs(File outputDir, String flameBaseName, File stacks) {
        Counter counter = profilerConfig.getCounter();
        String unit = counter == Counter.SAMPLES ? "samples" : "units";
        String titlePrefix = profilerConfig.getEvent().toUpperCase(Locale.ROOT);
        File flamegraph = new File(outputDir, flameBaseName + "-flames.svg");
        flamegraphGenerator.generateFlameGraph(
            stacks, flamegraph,
            "--colors", "java",
            "--minwidth", "1",
            "--title", titlePrefix + " Flame Graph",
            "--countname", unit
        );
        File iciclegraph = new File(outputDir, flameBaseName + "-icicles.svg");
        flamegraphGenerator.generateFlameGraph(
            stacks, iciclegraph,
            "--reverse", "--invert",
            "--colors", "java",
            "--minwidth", "2",
            "--title", titlePrefix + " Icicle Graph",
            "--countname", unit
        );
    }

    private File getProfilerScript() {
        return new File(profilerConfig.getProfilerHome(), "profiler.sh");
    }

    private static final class RemoveSystemThreads implements SanitizeFunction {

        @Override
        public List<String> map(List<String> stack) {
            for (String frame : stack) {
                if (frame.contains("GCTaskThread") || frame.contains("JavaThread")) {
                    return Collections.emptyList();
                }
            }
            return stack;
        }
    }

}
