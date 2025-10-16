package org.gradle.profiler.asyncprofiler;

import com.google.common.base.Joiner;
import org.gradle.profiler.JvmArgsCalculator;
import org.gradle.profiler.ScenarioSettings;
import sun.management.resources.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
        List<String> events = new ArrayList<>(profilerConfig.getEvents());
        boolean profileAllocations = events.size() > 1 && events.remove(AsyncProfilerConfig.EVENT_ALLOC);
        boolean profileLocks = events.size() > 1 && events.remove(AsyncProfilerConfig.EVENT_LOCK);

        StringBuilder agent = new StringBuilder()
            .append("-agentpath:").append(profilerConfig.getDistribution().getLibrary()).append("=start")
            .append(",event=").append(Joiner.on(",").join(events))
            .append(",interval=").append(profilerConfig.getInterval())
            .append(",jstackdepth=").append(profilerConfig.getStackDepth())
            .append(",").append(outputType.getCommandLineOption())
            .append(",").append(profilerConfig.getCounter().name().toLowerCase(Locale.ROOT))
            .append(",file=").append(outputType.individualOutputFileFor(scenarioSettings))
            .append(",ann");

        if (profileAllocations) {
            agent.append(",alloc=").append(profilerConfig.getAllocSampleSize());
        }
        if (profileLocks) {
            agent.append(",lock=").append(profilerConfig.getLockThreshold());
        }

        jvmArgs.add(agent.toString());
    }
}
