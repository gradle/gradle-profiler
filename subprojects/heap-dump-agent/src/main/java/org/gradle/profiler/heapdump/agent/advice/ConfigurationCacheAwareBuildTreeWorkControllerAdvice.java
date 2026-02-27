package org.gradle.profiler.heapdump.agent.advice;

import net.bytebuddy.asm.Advice;
import org.gradle.profiler.heapdump.agent.HeapDumpExecutor;
import org.gradle.profiler.heapdump.agent.bind.OutputPath;

/**
 * Generic advice for heap dumping that doesn't require a 'gradle' field.
 * It takes the strategy name from the first argument of the instrumented method.
 */
public class ConfigurationCacheAwareBuildTreeWorkControllerAdvice {
    @Advice.OnMethodExit()
    public static void onExit(
        @OutputPath String outputPath,
        @Advice.Argument(0) String tag
    ) {
        HeapDumpExecutor.invoke(outputPath, "cc-dump", tag);
    }
}
