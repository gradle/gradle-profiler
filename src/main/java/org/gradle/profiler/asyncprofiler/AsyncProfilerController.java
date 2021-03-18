package org.gradle.profiler.asyncprofiler;

import com.google.common.base.Joiner;
import org.gradle.profiler.CommandExec;
import org.gradle.profiler.GradleScenarioDefinition;
import org.gradle.profiler.InstrumentingProfiler;
import org.gradle.profiler.ScenarioSettings;
import org.gradle.profiler.flamegraph.FlameGraphSanitizer;
import org.gradle.profiler.flamegraph.FlameGraphTool;
import org.gradle.profiler.jfr.JfrFlameGraphGenerator;
import org.gradle.profiler.jfr.JfrToStacksConverter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

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
            pid
        );
    }

    @Override
    public void stopRecording(String pid) {
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
        List<JfrFlameGraphGenerator.Stacks> stacks = generateStacks(scenario.getOutputDir(), scenario.getProfileName(), this.stacks);
        new JfrFlameGraphGenerator().generateGraphs(scenario.getOutputDir(), stacks);
    }

    private List<JfrFlameGraphGenerator.Stacks> generateStacks(File outputDir, String outputBaseName, File stacksFile) {
        JfrToStacksConverter.EventType jfrEventType = convertAsyncEventToJfrEvent(profilerConfig.getEvent());
        List<JfrFlameGraphGenerator.Stacks> collectedStacks = new ArrayList<>();
        collectedStacks.add(sanitizeStacks(outputDir, outputBaseName, stacksFile, jfrEventType, JfrFlameGraphGenerator.DetailLevel.RAW, rawFlamegraphSanitizer));
        collectedStacks.add(sanitizeStacks(outputDir, outputBaseName, stacksFile, jfrEventType, JfrFlameGraphGenerator.DetailLevel.SIMPLIFIED, simplifiedFlamegraphSanitizer));

        return collectedStacks;
    }

    private JfrFlameGraphGenerator.Stacks sanitizeStacks(File outputDir, String outputBaseName, File stacksFileToConvert, JfrToStacksConverter.EventType jfrEventType, JfrFlameGraphGenerator.DetailLevel level, FlameGraphSanitizer flamegraphSanitizer) {
        String eventFileBaseName = getEventFileBaseName(outputBaseName, jfrEventType, level);
        File sanitizedStacksFile = new File(outputDir, eventFileBaseName + "-stacks.txt");
        flamegraphSanitizer.sanitize(stacksFileToConvert, sanitizedStacksFile);
        return new JfrFlameGraphGenerator.Stacks(sanitizedStacksFile, jfrEventType, level, eventFileBaseName);
    }

    private String getEventFileBaseName(String outputBaseName, JfrToStacksConverter.EventType type, JfrFlameGraphGenerator.DetailLevel level) {
        return Joiner.on("-").join(outputBaseName, type.getId(), level.name().toLowerCase(Locale.ROOT));
    }

    private JfrToStacksConverter.EventType convertAsyncEventToJfrEvent(String event) {
        switch (event) {
            case "cpu":
                return JfrToStacksConverter.EventType.CPU;
            case "wall":
                return JfrToStacksConverter.EventType.CPU;
            case "alloc":
                return JfrToStacksConverter.EventType.ALLOCATION;
            case "lock":
                return JfrToStacksConverter.EventType.MONITOR_BLOCKED;
            default:
                return JfrToStacksConverter.EventType.CPU;
        }
    }

    private void validateStacks() {
        if (!stacks.isFile() || stacks.length() == 0) {
            throw new RuntimeException("No stacks have been capture by Async profiler. If you are on Linux, you may need to set two runtime variables:\n" +
                "# sysctl kernel.perf_event_paranoid=1\n" +
                "# sysctl kernel.kptr_restrict=0");
        }
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
