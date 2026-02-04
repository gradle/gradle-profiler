package org.gradle.profiler.lifecycle.agent.strategy;

import org.gradle.profiler.lifecycle.agent.HeapDumpInterceptor;

/**
 * Strategy that captures heap dumps when the configuration phase ends and
 * the execution phase begins. Intercepts finalizeWorkGraph() method.
 */
public class ConfigEndStrategy implements HeapDumpStrategy {

    @Override
    public String getOptionValue() {
        return "config-end";
    }

    @Override
    public String getFilePrefix() {
        return "gradle-config-end";
    }

    @Override
    public String getInterceptionMessage() {
        return "Configuration Stage Ending";
    }

    @Override
    public String getTargetMethodName() {
        return "finalizeWorkGraph";
    }

    @Override
    public String getTargetMethodDescriptor() {
        return "(Lorg/gradle/internal/build/BuildLifecycleController$BuildWorkPlan;)V";
    }

    /**
     * Static entry point called from instrumented code.
     */
    public static void onFinalizeWorkGraph(String outputPath) {
        new HeapDumpInterceptor(outputPath, new ConfigEndStrategy()).intercept();
    }
}
