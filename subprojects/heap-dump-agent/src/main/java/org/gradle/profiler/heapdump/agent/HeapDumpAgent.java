package org.gradle.profiler.heapdump.agent;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import org.gradle.profiler.heapdump.agent.advice.ConfigurationCacheAwareBuildTreeWorkControllerAdvice;
import org.gradle.profiler.heapdump.agent.advice.DefaultBuildLifecycleControllerAdvice;
import org.gradle.profiler.heapdump.agent.bind.OutputPath;
import org.gradle.profiler.heapdump.agent.bind.StrategyName;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.util.Set;
import java.util.jar.JarFile;

import static net.bytebuddy.matcher.ElementMatchers.named;

public class HeapDumpAgent {

    public static void premain(String agentArgs, Instrumentation instrumentation) throws IOException {
        System.out.println("HEAP_DUMP_AGENT: Loaded");

        if (agentArgs == null) {
            throw new IllegalArgumentException("Agent arguments must be provided: <runtimeJar>;<outputDir>;<scenarioName>;<strategies>");
        }

        String[] parts = agentArgs.split(";", 4);
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid heap dump agent arguments.");
        }

        String runtimeJarPath = parts[0];
        String outputPath = parts[1];
        // parts[2] is scenarioName, which we don't use in the agent
        Set<String> strategies = parts.length >= 4 ? Set.of(parts[3].trim().split(",")) : Set.of("build-end");

        for (String strategy : strategies) {
            switch (strategy) {
                case "config-end":
                    new AgentBuilder.Default()
                        .type(named("org.gradle.internal.build.DefaultBuildLifecycleController"))
                        .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                            builder.visit(
                                Advice.withCustomMapping()
                                    .bind(StrategyName.class, "config-end")
                                    .bind(OutputPath.class, outputPath)
                                    .to(DefaultBuildLifecycleControllerAdvice.class)
                                    .on(named("finalizeWorkGraph"))
                            )
                        )
                        .installOn(instrumentation);
                    break;
                case "build-end":
                    new AgentBuilder.Default()
                        .type(named("org.gradle.internal.build.DefaultBuildLifecycleController"))
                        .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                            builder.visit(
                                Advice.withCustomMapping()
                                    .bind(StrategyName.class, "build-end")
                                    .bind(OutputPath.class, outputPath)
                                    .to(DefaultBuildLifecycleControllerAdvice.class)
                                    .on(named("finishBuild"))
                            )
                        )
                        .installOn(instrumentation);
                    break;
                case "cc":
                    new AgentBuilder.Default()
                        .type(named("org.gradle.internal.cc.impl.ConfigurationCacheAwareBuildTreeWorkController"))
                        .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                            builder.visit(
                                Advice.withCustomMapping()
                                    .bind(StrategyName.class, "cc-dump")
                                    .bind(OutputPath.class, outputPath)
                                    .to(ConfigurationCacheAwareBuildTreeWorkControllerAdvice.class)
                                    .on(named("maybeDumpHeap"))
                            )
                        )
                        .installOn(instrumentation);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown heap dump strategy: " + strategy);
            }
        }

        // Add the runtime jar to the bootstrap classloader so that the advice can be loaded by the same classloader as the target class.
        JarFile runtimeJar = new JarFile(runtimeJarPath);
        instrumentation.appendToBootstrapClassLoaderSearch(runtimeJar);
    }

}
