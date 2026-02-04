package org.gradle.profiler.lifecycle.agent.strategy;

import org.gradle.profiler.lifecycle.agent.HeapDumpInterceptor;

/**
 * Strategy that captures heap dumps when the build completes (after all tasks execute).
 * Intercepts finishBuild() method.
 */
public class BuildEndStrategy implements HeapDumpStrategy {

    @Override
    public String getOptionValue() {
        return "build-end";
    }

    @Override
    public String getFilePrefix() {
        return "gradle-build-end";
    }

    @Override
    public String getInterceptionMessage() {
        return "Build Finishing";
    }

    @Override
    public String getTargetMethodName() {
        return "finishBuild";
    }

    @Override
    public String getTargetMethodDescriptor() {
        return "(Ljava/lang/Throwable;)Lorg/gradle/internal/build/ExecutionResult;";
    }

    /**
     * Static entry point called from instrumented code.
     */
    public static void onFinishBuild(String outputPath) {
        new HeapDumpInterceptor(outputPath, new BuildEndStrategy()).intercept();
    }
}
