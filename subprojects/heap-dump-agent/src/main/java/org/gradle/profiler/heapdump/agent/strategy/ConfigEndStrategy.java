package org.gradle.profiler.heapdump.agent.strategy;

/**
 * Strategy that captures heap dumps when the configuration phase ends and
 * the execution phase begins. Intercepts finalizeWorkGraph() method.
 */
public class ConfigEndStrategy implements HeapDumpStrategy {

    @Override
    public String getName() {
        return "config-end";
    }

    @Override
    public String getTargetClassName() {
        return "org/gradle/internal/build/DefaultBuildLifecycleController";
    }

    @Override
    public String getTargetMethodName() {
        return "finalizeWorkGraph";
    }

    @Override
    public String getTargetMethodDescriptor() {
        return "(Lorg/gradle/internal/build/BuildLifecycleController$BuildWorkPlan;)V";
    }
}
