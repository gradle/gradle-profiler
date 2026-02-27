package org.gradle.profiler.heapdump.agent.advice;

import net.bytebuddy.asm.Advice;
import org.gradle.profiler.heapdump.agent.GradleReflectionUtils;
import org.gradle.profiler.heapdump.agent.HeapDumpExecutor;
import org.gradle.profiler.heapdump.agent.bind.OutputPath;
import org.gradle.profiler.heapdump.agent.bind.StrategyName;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Advice for heap dumping in classes that have a 'gradle' field (e.g. DefaultBuildLifecycleController).
 * This allows including the project name in the heap dump metadata.
 */
public class DefaultBuildLifecycleControllerAdvice {
    @Advice.OnMethodExit()
    public static void onExit(
        @OutputPath String outputPath,
        @StrategyName String strategyName,
        @Advice.FieldValue("gradle") Object gradle
    ) throws ReflectiveOperationException {
        String projectName = GradleReflectionUtils.getProjectName(gradle);
        HeapDumpExecutor.invoke(outputPath, strategyName, projectName);
    }
}
