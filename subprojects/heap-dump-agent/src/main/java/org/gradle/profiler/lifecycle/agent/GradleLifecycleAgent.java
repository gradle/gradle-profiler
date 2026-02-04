package org.gradle.profiler.lifecycle.agent;

import org.gradle.profiler.lifecycle.agent.strategy.BuildEndStrategy;
import org.gradle.profiler.lifecycle.agent.strategy.ConfigEndStrategy;
import org.gradle.profiler.lifecycle.agent.strategy.HeapDumpStrategy;
import org.objectweb.asm.*;

import java.io.File;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

/**
 * Java agent that instruments Gradle's DefaultBuildLifecycleController to capture heap dumps
 * at various points in the build lifecycle based on configured strategies.
 */
public class GradleLifecycleAgent {

    private static final List<HeapDumpStrategy> AVAILABLE_STRATEGIES = Arrays.asList(
        new ConfigEndStrategy(),
        new BuildEndStrategy()
    );

    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("!!! Gradle Lifecycle Agent Started");

        // Parse agent arguments: format is "<outputDir>;<strategy1>,<strategy2>"
        // Example: "/path/to/output;config-end,build-end"
        String outputPath = System.getProperty("user.dir");
        List<HeapDumpStrategy> strategies = new ArrayList<>();
        strategies.add(new BuildEndStrategy()); // default

        if (agentArgs != null && !agentArgs.isEmpty()) {
            String[] parts = agentArgs.split(";", 2);
            outputPath = parts[0];

            if (parts.length > 1) {
                strategies.clear();
                String[] strategyNames = parts[1].split(",");
                for (String strategyName : strategyNames) {
                    String trimmedName = strategyName.trim();
                    HeapDumpStrategy found = findStrategyByName(trimmedName);
                    if (found != null) {
                        strategies.add(found);
                    } else {
                        System.err.println("ERROR: Unknown interception strategy: " + trimmedName);
                    }
                }
            }
        }

        System.out.println("!!! Heap dump output directory: " + outputPath);
        System.out.println("!!! Active strategies: " + strategies.stream()
            .map(HeapDumpStrategy::getOptionValue)
            .collect(Collectors.toList()));

        // Add this agent's JAR to bootstrap classloader search path
        // This makes interceptor classes available to all classloaders
        try {
            File agentJar = new File(GradleLifecycleAgent
                .class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI()
            );
            inst.appendToBootstrapClassLoaderSearch(new JarFile(agentJar));
            System.out.println("!!! Added agent JAR to bootstrap classloader: " + agentJar);
        } catch (Exception e) {
            System.err.println("ERROR: Failed to add agent JAR to bootstrap classloader");
            e.printStackTrace();
        }

        // Register transformer that instruments methods based on active strategies
        inst.addTransformer(new LifecycleInstrumentingTransformer(outputPath, strategies));
    }

    private static HeapDumpStrategy findStrategyByName(String name) {
        for (HeapDumpStrategy strategy : AVAILABLE_STRATEGIES) {
            if (strategy.getOptionValue().equals(name)) {
                return strategy;
            }
        }
        return null;
    }

    private static class LifecycleInstrumentingTransformer implements ClassFileTransformer {
        private static final String TARGET_CLASS = "org/gradle/internal/build/DefaultBuildLifecycleController";

        private final String outputPath;
        private final List<HeapDumpStrategy> strategies;

        public LifecycleInstrumentingTransformer(String outputPath, List<HeapDumpStrategy> strategies) {
            this.outputPath = outputPath;
            this.strategies = strategies;
        }

        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] originalByteCode) {
            if (className.equals(TARGET_CLASS)) {
                System.out.println("!!! Instrumenting DefaultBuildLifecycleController");
                return instrumentLifecycleController(originalByteCode);
            }
            return null;
        }

        private byte[] instrumentLifecycleController(byte[] originalByteCode) {
            ClassReader reader = new ClassReader(originalByteCode);
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);

            ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);

                    // Check if this method should be instrumented based on active strategies
                    for (HeapDumpStrategy strategy : strategies) {
                        if (shouldInstrumentMethod(strategy, name, descriptor)) {
                            System.out.println("!!! Instrumenting " + name + " for " + strategy.getOptionValue());
                            return createInstrumentingMethodVisitor(methodVisitor, strategy);
                        }
                    }

                    return methodVisitor;
                }
            };

            reader.accept(visitor, 0);
            return writer.toByteArray();
        }

        private boolean shouldInstrumentMethod(HeapDumpStrategy strategy, String methodName, String descriptor) {
            return methodName.equals(strategy.getTargetMethodName()) &&
                   descriptor.equals(strategy.getTargetMethodDescriptor());
        }

        private MethodVisitor createInstrumentingMethodVisitor(MethodVisitor mv, HeapDumpStrategy strategy) {
            return new MethodVisitor(Opcodes.ASM9, mv) {
                @Override
                public void visitCode() {
                    super.visitCode();
                    // Inject call to appropriate strategy's static entry point at method start
                    visitLdcInsn(outputPath);
                    visitMethodInsn(Opcodes.INVOKESTATIC,
                        getInterceptorClassName(strategy),
                        getEntryPointMethodName(strategy),
                        "(Ljava/lang/String;)V",
                        false);
                }
            };
        }

        private String getInterceptorClassName(HeapDumpStrategy strategy) {
            if (strategy instanceof ConfigEndStrategy) {
                return "org/gradle/profiler/lifecycle/agent/strategy/ConfigEndStrategy";
            } else if (strategy instanceof BuildEndStrategy) {
                return "org/gradle/profiler/lifecycle/agent/strategy/BuildEndStrategy";
            } else {
                throw new IllegalArgumentException("Unknown strategy: " + strategy.getClass().getName());
            }
        }

        private String getEntryPointMethodName(HeapDumpStrategy strategy) {
            if (strategy instanceof ConfigEndStrategy) {
                return "onFinalizeWorkGraph";
            } else if (strategy instanceof BuildEndStrategy) {
                return "onFinishBuild";
            } else {
                throw new IllegalArgumentException("Unknown strategy: " + strategy.getClass().getName());
            }
        }
    }
}
