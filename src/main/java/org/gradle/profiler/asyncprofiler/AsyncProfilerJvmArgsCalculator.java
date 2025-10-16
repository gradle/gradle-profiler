package org.gradle.profiler.asyncprofiler;

import com.google.common.base.Joiner;
import org.gradle.profiler.JvmArgsCalculator;
import org.gradle.profiler.ScenarioSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.gradle.profiler.asyncprofiler.AsyncProfilerDistribution.Version.AP_3_0;

class AsyncProfilerJvmArgsCalculator implements JvmArgsCalculator {
    private final AsyncProfilerConfig profilerConfig;
    private final ScenarioSettings scenarioSettings;
    private final AsyncProfilerOutputType outputType;

    AsyncProfilerJvmArgsCalculator(AsyncProfilerConfig profilerConfig, ScenarioSettings scenarioSettings) {
        this.profilerConfig = profilerConfig;
        this.scenarioSettings = scenarioSettings;
        this.outputType = AsyncProfilerOutputType.from(profilerConfig, scenarioSettings.getScenario());
    }

    @Override
    public void calculateJvmArgs(List<String> jvmArgs) {
        // TODO support --all events
        //  e.g. -agentpath:/path/to/libasyncProfiler.so=start,all,alloc=2m,lock=10ms,file=profile.jfr
        //       -agentpath:/path/to/libasyncProfiler.so=start,all,event=cycles,nativemem=10,lock=100,alloc=1000,wall=10000,proc=10,file=%f.jfr

        //  | Events            | Generated Command                                                    |
        //  |-------------------|----------------------------------------------------------------------|
        //  | ["alloc"]         | -agentpath:/path/to/libasyncProfiler.dylib=start,alloc=524287,file=out.jfr |
        //  | ["cpu"]           | -agentpath:/path/to/libasyncProfiler.dylib=start,event=cpu,interval=10000000,file=out.jfr |
        //  | ["cpu", "alloc"]  | -agentpath:/path/to/libasyncProfiler.dylib=start,event=cpu,interval=10000000,alloc=524287,file=out.jfr |
        //  | ["alloc"]         | -agentpath:/path/to/libasyncProfiler.dylib=start,alloc=524287,file=out.jfr |

        List<String> events = new ArrayList<>(profilerConfig.getEvents());

        // For agent-based profiling: alloc/lock/wall always use auxiliary options (both 2.9 and 3.0+)
        boolean useAllocOption = events.remove(AsyncProfilerConfig.EVENT_ALLOC);
        boolean useLockOption = events.remove(AsyncProfilerConfig.EVENT_LOCK);
        boolean useWallOption = events.remove(AsyncProfilerConfig.EVENT_WALL);

        // Determine the interval for remaining events
        int intervalToUse = profilerConfig.getInterval();

        StringBuilder agent = new StringBuilder()
            .append("-agentpath:").append(profilerConfig.getDistribution().getLibrary()).append("=start");

        // event= and interval= only needed for non-auxiliary events (cpu, cycles, etc.)
        if (!events.isEmpty()) {
            agent.append(",event=").append(Joiner.on(",").join(events))
                .append(",interval=").append(intervalToUse);
        }

        agent.append(",jstackdepth=").append(profilerConfig.getStackDepth())
            .append(",").append(outputType.getCommandLineOption())
            .append(",").append(profilerConfig.getCounter().name().toLowerCase(Locale.ROOT))
            .append(",file=").append(outputType.individualOutputFileFor(scenarioSettings))
            .append(",ann");

        if (useAllocOption) {
            agent.append(",alloc=").append(profilerConfig.getAllocSampleSize());
        }
        if (useLockOption) {
            agent.append(",lock=").append(profilerConfig.getLockThreshold());
        }
        if (useWallOption) {
            agent.append(",wall=").append(profilerConfig.getWallInterval());
        }

        jvmArgs.add(agent.toString());
    }
}
