package org.gradle.profiler.asyncprofiler;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.gradle.profiler.CommandExec;
import org.gradle.profiler.InstrumentingProfiler;
import org.gradle.profiler.ScenarioSettings;
import org.gradle.profiler.flamegraph.DetailLevel;
import org.gradle.profiler.flamegraph.EventType;
import org.gradle.profiler.flamegraph.FlameGraphGenerator;
import org.gradle.profiler.flamegraph.FlameGraphSanitizer;
import org.gradle.profiler.flamegraph.Stacks;
import org.gradle.profiler.flamegraph.JfrToStacksConverter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.gradle.profiler.asyncprofiler.AsyncProfilerDistribution.Version.AP_3_0;
import static org.gradle.profiler.flamegraph.FlameGraphSanitizer.SanitizeFunction;

public class AsyncProfilerController implements InstrumentingProfiler.SnapshotCapturingProfilerController {
    private final AsyncProfilerConfig profilerConfig;
    private final ScenarioSettings scenarioSettings;
    private final JfrToStacksConverter stacksConverter;
    private final FlameGraphGenerator flameGraphGenerator;
    private final ImmutableMap<DetailLevel, FlameGraphSanitizer> flameGraphSanitizers;
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
            DetailLevel.RAW, rawFlamegraphSanitizer,
            DetailLevel.SIMPLIFIED, simplifiedFlamegraphSanitizer
        );
        this.stacksConverter = new JfrToStacksConverter(flameGraphSanitizers);
        this.flameGraphGenerator = new FlameGraphGenerator();
        this.outputType = AsyncProfilerOutputType.from(profilerConfig, scenarioSettings.getScenario());
        this.outputFile = outputType.outputFileFor(scenarioSettings);
    }

    public String getName() {
        return "async profiler";
    }

    @Override
    public void startRecording(String pid) throws IOException, InterruptedException {
        // TODO support all events, custom options ?
        //  e.g. asprof --all -e cycles --alloc 2m --lock 10ms -f profile.jfr

        //  | Version | Events            | Generated Command                                                    |
        //  |---------|-------------------|----------------------------------------------------------------------|
        //  | 2.9     | ["alloc"]         | profiler.sh start -e alloc -i 524287 -f out.jfr <pid>                |
        //  | 2.9     | ["cpu"]           | profiler.sh start -e cpu -i 10000000 -f out.jfr <pid>                |
        //  | 2.9     | ["cpu", "alloc"]  | profiler.sh start -e cpu -i 10000000 --alloc 524287 -f out.jfr <pid> |
        //  | 3.0+    | ["alloc"]         | asprof start --alloc 524287 -f out.jfr <pid>                         |
        //  | 3.0+    | ["cpu", "alloc"]  | asprof start -e cpu -i 10000000 --alloc 524287 -f out.jfr <pid>      |
        //  | 3.0+    | ["wall"]          | asprof start --wall 10000000 -f out.jfr <pid>                        |
        //  | 3.0+    | ["cpu", "wall"]   | asprof start -e cpu -i 10000000 --wall 10000000 -f out.jfr <pid>     |
        //  | 3.0+    | ["alloc", "lock"] | asprof start --alloc 524287 --lock 250000 -f out.jfr <pid>           |

        List<String> events = new ArrayList<>(profilerConfig.getEvents());
        boolean ap3plus = profilerConfig.getDistribution().getVersion().compareTo(AP_3_0) >= 0;

        // v3.0+: alloc/lock/wall always become auxiliary options
        // v2.9: if multiple events, remove alloc/lock/wall and use as auxiliary options
        boolean useAllocOption = (ap3plus || events.size() > 1) && events.remove(AsyncProfilerConfig.EVENT_ALLOC);
        boolean useLockOption= (ap3plus || events.size() > 1) && events.remove(AsyncProfilerConfig.EVENT_LOCK);
        boolean useWallOption= (ap3plus || events.size() > 1) && events.remove(AsyncProfilerConfig.EVENT_WALL);

        // v2.9: sonly supports a single event in -e
        if (!ap3plus && events.size() > 1) {
            events.subList(1, events.size()).clear();
        }

        // Determine the interval to use based on the event type
        int intervalToUse = profilerConfig.getInterval();
        // If the single remaining event is alloc/lock/wall, use its specific interval with -i
        if (events.size() == 1) {
            String singleEvent = events.get(0);
            if (AsyncProfilerConfig.EVENT_ALLOC.equals(singleEvent)) {
                intervalToUse = profilerConfig.getAllocSampleSize();
            } else if (AsyncProfilerConfig.EVENT_LOCK.equals(singleEvent)) {
                intervalToUse = profilerConfig.getLockThreshold();
            } else if (AsyncProfilerConfig.EVENT_WALL.equals(singleEvent)) {
                intervalToUse = profilerConfig.getWallInterval();
            }
        }

        ImmutableList.Builder<String> arguments = ImmutableList.builder();
        arguments.add(
            profilerConfig.getDistribution().getExecutable().getAbsolutePath(),
            "start"
        );

        // -e and -i: for v3.0+ only used for primary events; for v<3.0 used for the single event
        if (!events.isEmpty()) {
            arguments.add("-e", Joiner.on(",").join(events));
            arguments.add("-i", String.valueOf(intervalToUse));
        }

        arguments.add(
            "-j", String.valueOf(profilerConfig.getStackDepth()),
            "--" + profilerConfig.getCounter().name().toLowerCase(Locale.ROOT),
            "--ann", // annotate java methods
            "-o", outputType.getCommandLineOption(),
            "-f", outputType.individualOutputFileFor(scenarioSettings).getAbsolutePath()
        );
        if (useAllocOption) {
            arguments.add("--alloc", String.valueOf(profilerConfig.getAllocSampleSize()));
        }
        if (useLockOption) {
            arguments.add("--lock", String.valueOf(profilerConfig.getLockThreshold()));
        }
        if (useWallOption) {
            arguments.add("--wall", String.valueOf(profilerConfig.getWallInterval()));
        }
        arguments.add(pid);
        new CommandExec().run(arguments.build());
    }

    @Override
    public void stopRecording(String pid) {
        new CommandExec().run(
            profilerConfig.getDistribution().getExecutable().getAbsolutePath(),
            "stop",
            "-o", outputType.getCommandLineOption(),
            "-f", outputType.individualOutputFileFor(scenarioSettings).getAbsolutePath(),
            "-ann", // annotate java methods
            pid
        );
    }

    @Override
    public void captureSnapshot(String pid) {
    }

    @Override
    public void stopSession() {
        List<Stacks> stacks = generateStacks(scenarioSettings.getProfilerOutputBaseDir(), scenarioSettings.getProfilerOutputBaseName());
        flameGraphGenerator.generateGraphs(scenarioSettings.getProfilerOutputBaseDir(), stacks);
    }

    private List<Stacks> generateStacks(File outputDir, String outputBaseName) {
        if (outputType == AsyncProfilerOutputType.JFR) {
            List<Stacks> stacks = stacksConverter.generateStacks(outputFile, outputBaseName);
            if (stacks.isEmpty()) {
                failOnEmptyStacks();
            }
            return stacks;
        } else {
            validateStacks();
            List<Stacks> collectedStacks = new ArrayList<>();
            for (String event : profilerConfig.getEvents()) {
                EventType jfrEventType = convertAsyncEventToJfrEvent(event);
                for (DetailLevel level : DetailLevel.values()) {
                    collectedStacks.add(sanitizeStacks(outputDir, outputBaseName, outputFile, jfrEventType, level));
                }
            }
            return collectedStacks;
        }
    }

    private Stacks sanitizeStacks(File outputDir, String outputBaseName, File stacksFileToConvert, EventType jfrEventType, DetailLevel level) {
        FlameGraphSanitizer flamegraphSanitizer = flameGraphSanitizers.get(level);
        String eventFileBaseName = outputBaseName + Stacks.postFixFor(jfrEventType, level);
        File sanitizedStacksFile = new File(outputDir, eventFileBaseName + Stacks.STACKS_FILE_SUFFIX);
        flamegraphSanitizer.sanitize(stacksFileToConvert, sanitizedStacksFile);
        return new Stacks(sanitizedStacksFile, jfrEventType, level, eventFileBaseName);
    }

    private EventType convertAsyncEventToJfrEvent(String event) {
        switch (event) {
            case "cpu":
                return EventType.CPU;
            case "wall":
                return EventType.WALL;
            case "alloc":
                return EventType.ALLOCATION;
            case "lock":
                return EventType.MONITOR_BLOCKED;
            default:
                return EventType.CPU;
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
