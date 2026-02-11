package org.gradle.profiler.heapdump.agent.strategy;

/**
 * Strategy that captures heap dumps when the build completes (after all tasks execute).
 * Intercepts finishBuild() method.
 */
public class BuildEndStrategy implements HeapDumpStrategy {

    @Override
    public String getName() {
        return "build-end";
    }

    @Override
    public String getTargetClassName() {
        return "org/gradle/internal/build/DefaultBuildLifecycleController";
    }

    @Override
    public String getTargetMethodName() {
        return "finishBuild";
    }

    @Override
    public String getTargetMethodDescriptor() {
        return "(Ljava/lang/Throwable;)Lorg/gradle/internal/build/ExecutionResult;";
    }
}
