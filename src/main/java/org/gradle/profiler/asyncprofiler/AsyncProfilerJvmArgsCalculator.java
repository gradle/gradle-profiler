package org.gradle.profiler.asyncprofiler;

import org.gradle.profiler.JvmArgsCalculator;
import org.gradle.profiler.ScenarioSettings;

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
        StringBuilder agent = new StringBuilder()
            .append("-agentpath:").append(profilerConfig.getProfilerHome()).append("/build/libasyncProfiler.so=start")
            .append(",event=").append(profilerConfig.getJoinedEvents())
            .append(",interval=").append(profilerConfig.getInterval())
            .append(",jstackdepth=").append(profilerConfig.getStackDepth())
            .append(",").append(outputType.getCommandLineOption())
            .append(",").append(profilerConfig.getCounter().name().toLowerCase(Locale.ROOT))
            .append(",file=").append(outputType.outputFileFor(scenarioSettings))
            .append(",ann");

        jvmArgs.add(agent.toString());
    }
}
