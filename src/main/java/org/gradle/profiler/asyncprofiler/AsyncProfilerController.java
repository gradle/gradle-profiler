package org.gradle.profiler.asyncprofiler;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.gradle.profiler.CommandExec;
import org.gradle.profiler.InstrumentingProfiler;
import org.gradle.profiler.ScenarioSettings;
import org.gradle.profiler.flamegraph.FlameGraphSanitizer;
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
    private final JfrFlameGraphGenerator flameGraphGenerator;
    private final ImmutableMap<JfrFlameGraphGenerator.DetailLevel, FlameGraphSanitizer> flameGraphSanitizers;
    private final File outputFile;
    private final AsyncProfilerOutputType outputType;

    public AsyncProfilerController(AsyncProfilerConfig profilerConfig, ScenarioSettings scenarioSettings) {
        this.profilerConfig = profilerConfig;
        this.scenarioSettings = scenarioSettings;
        FlameGraphSanitizer rawFlamegraphSanitizer = FlameGraphSanitizer.raw();
        FlameGraphSanitizer simplifiedFlamegraphSanitizer = profilerConfig.isIncludeSystemThreads()
            ? FlameGraphSanitizer.simplified()
            : FlameGraphSanitizer.simplified(new RemoveSystemThreads());
        this.flameGraphSanitizers = ImmutableMap.of(
            JfrFlameGraphGenerator.DetailLevel.RAW, rawFlamegraphSanitizer,
            JfrFlameGraphGenerator.DetailLevel.SIMPLIFIED, simplifiedFlamegraphSanitizer
        );
        this.flameGraphGenerator = new JfrFlameGraphGenerator(flameGraphSanitizers);
        this.outputType = AsyncProfilerOutputType.from(profilerConfig, scenarioSettings.getScenario());
        this.outputFile = outputType.outputFileFor(scenarioSettings);
    }

    public String getName() {
        return "async profiler";
    }

    @Override
    public void startRecording(String pid) throws IOException, InterruptedException {
        ImmutableList.Builder<String> arguments = ImmutableList.builder();
        arguments.add(
            getProfilerScript().getAbsolutePath(),
            "start",
            "-e", profilerConfig.getJoinedEvents(),
            "-i", String.valueOf(profilerConfig.getInterval()),
            "-j", String.valueOf(profilerConfig.getStackDepth()),
            "--" + profilerConfig.getCounter().name().toLowerCase(Locale.ROOT),
            "-a",
            "-o", outputType.getCommandLineOption(),
            "-f", outputType.individualOutputFileFor(scenarioSettings).getAbsolutePath()
        );
        if (profilerConfig.getEvents().contains(AsyncProfilerConfig.EVENT_ALLOC)) {
            arguments.add("--alloc", String.valueOf(profilerConfig.getAllocSampleSize()));
        }
        if (profilerConfig.getEvents().contains(AsyncProfilerConfig.EVENT_LOCK)) {
            arguments.add("--lock", String.valueOf(profilerConfig.getLockThreshold()));
        }
        arguments.add(pid);
        new CommandExec().run(arguments.build());
    }

    @Override
    public void stopRecording(String pid) {
        new CommandExec().run(
            getProfilerScript().getAbsolutePath(),
            "stop",
            "-o", outputType.getCommandLineOption(),
            "-f", outputType.individualOutputFileFor(scenarioSettings).getAbsolutePath(),
            "-a",
            pid
        );
    }

    @Override
    public void captureSnapshot(String pid) {
    }

    @Override
    public void stopSession() {
        List<JfrFlameGraphGenerator.Stacks> stacks = generateStacks(scenarioSettings.getProfilerOutputBaseDir(), scenarioSettings.getProfilerOutputBaseName());
        flameGraphGenerator.generateGraphs(scenarioSettings.getProfilerOutputBaseDir(), stacks);
    }

    private List<JfrFlameGraphGenerator.Stacks> generateStacks(File outputDir, String outputBaseName) {
        if (outputType == AsyncProfilerOutputType.JFR) {
            List<JfrFlameGraphGenerator.Stacks> stacks = flameGraphGenerator.generateStacks(outputFile, outputBaseName);
            if (stacks.isEmpty()) {
                failOnEmptyStacks();
            }
            return stacks;
        } else {
            validateStacks();
            List<JfrFlameGraphGenerator.Stacks> collectedStacks = new ArrayList<>();
            for (String event : profilerConfig.getEvents()) {
                JfrToStacksConverter.EventType jfrEventType = convertAsyncEventToJfrEvent(event);
                for (JfrFlameGraphGenerator.DetailLevel level : JfrFlameGraphGenerator.DetailLevel.values()) {
                    collectedStacks.add(sanitizeStacks(outputDir, outputBaseName, outputFile, jfrEventType, level));
                }
            }
            return collectedStacks;
        }
    }

    private JfrFlameGraphGenerator.Stacks sanitizeStacks(File outputDir, String outputBaseName, File stacksFileToConvert, JfrToStacksConverter.EventType jfrEventType, JfrFlameGraphGenerator.DetailLevel level) {
        FlameGraphSanitizer flamegraphSanitizer = flameGraphSanitizers.get(level);
        String eventFileBaseName = eventFileBaseNameFor(outputBaseName, jfrEventType, level);
        File sanitizedStacksFile = new File(outputDir, eventFileBaseName + "-stacks.txt");
        flamegraphSanitizer.sanitize(stacksFileToConvert, sanitizedStacksFile);
        return new JfrFlameGraphGenerator.Stacks(sanitizedStacksFile, jfrEventType, level, eventFileBaseName);
    }

    private String eventFileBaseNameFor(String outputBaseName, JfrToStacksConverter.EventType type, JfrFlameGraphGenerator.DetailLevel level) {
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
        if (!outputFile.isFile() || outputFile.length() == 0) {
            failOnEmptyStacks();
        }
    }

    private void failOnEmptyStacks() {
        throw new RuntimeException("No stacks have been captured by Async profiler. If you are on Linux, you may need to set two runtime variables:\n" +
            "# sysctl kernel.perf_event_paranoid=1\n" +
            "# sysctl kernel.kptr_restrict=0");
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
