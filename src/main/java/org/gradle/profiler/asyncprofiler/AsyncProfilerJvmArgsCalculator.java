package org.gradle.profiler.asyncprofiler;

import org.gradle.profiler.GradleScenarioDefinition;
import org.gradle.profiler.Invoker;
import org.gradle.profiler.JvmArgsCalculator;
import org.gradle.profiler.ScenarioSettings;

import java.io.File;
import java.util.List;
import java.util.Locale;

class AsyncProfilerJvmArgsCalculator implements JvmArgsCalculator {
    private final AsyncProfilerConfig profilerConfig;
    private final ScenarioSettings scenarioSettings;

    AsyncProfilerJvmArgsCalculator(AsyncProfilerConfig profilerConfig, ScenarioSettings scenarioSettings) {
        this.profilerConfig = profilerConfig;
        this.scenarioSettings = scenarioSettings;
    }

    @Override
    public void calculateJvmArgs(List<String> jvmArgs) {
        if (scenarioSettings.getScenario().getInvoker() == Invoker.NoDaemon) {
            StringBuilder agent = new StringBuilder()
                .append("-agentpath:").append(profilerConfig.getProfilerHome()).append("/build/libasyncProfiler.so=start")
                .append(",event=").append(profilerConfig.getEvent())
                .append(",interval=").append(profilerConfig.getInterval())
                .append(",jstackdepth=").append(profilerConfig.getStackDepth())
                .append(",buffer=").append(profilerConfig.getFrameBuffer())
                .append(",collapsed=").append(profilerConfig.getCounter().name().toLowerCase(Locale.ROOT))
                .append(",ann");

            GradleScenarioDefinition scenario = scenarioSettings.getScenario();
            File stacks = AsyncProfiler.stacksFileFor(scenario);
            agent.append(",file=").append(stacks.getAbsolutePath());

            jvmArgs.add(agent.toString());
        }

    }

}
