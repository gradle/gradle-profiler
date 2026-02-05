package org.gradle.profiler.heapdump.agent;

import org.gradle.profiler.heapdump.agent.strategy.BuildEndStrategy;
import org.gradle.profiler.heapdump.agent.strategy.ConfigEndStrategy;
import org.gradle.profiler.heapdump.agent.strategy.HeapDumpStrategy;
import org.objectweb.asm.*;

import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public class HeapDumpAgent {

    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("HEAP_DUMP_AGENT: Loaded");

        if (agentArgs == null) {
            throw new IllegalArgumentException("Agent arguments must be provided: <runtimeJar>;<outputDir>;<strategies>");
        }

        String[] parts = agentArgs.split(";", 3);
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid heap dump agent arguments.");
        }

        String runtimeJarPath = parts[0];
        String outputPath = parts[1];

        Set<HeapDumpStrategy> selectedStrategies = new HashSet<>();
        // We set a default strategy if none are specified
        selectedStrategies.add(new BuildEndStrategy());
        // Based on specified strategies, add to the set
        if (parts.length == 3 && !parts[2].isEmpty()) {
            String[] strategyNames = parts[2].split(",");
            selectedStrategies.clear();
            for (String name : strategyNames) {
                switch (name.trim()) {
                    case "config-end":
                        selectedStrategies.add(new ConfigEndStrategy());
                        break;
                    case "build-end":
                        selectedStrategies.add(new BuildEndStrategy());
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown strategy: " + name + ". Available strategies: config-end, build-end");
                }
            }
        }

        System.out.println("HEAP_DUMP_AGENT: Heap dump output directory: " + outputPath);
        System.out.println("HEAP_DUMP_AGENT: Active strategies: " + selectedStrategies.stream()
            .map(HeapDumpStrategy::getName)
            .collect(Collectors.toList()));
        // Register transformer that instruments methods based on active strategies
        inst.addTransformer(new LifecycleInstrumentingTransformer(outputPath, selectedStrategies));

        try {
            // Make the runtime JAR available to bootstrap classloader
            inst.appendToBootstrapClassLoaderSearch(new JarFile(runtimeJarPath));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static class LifecycleInstrumentingTransformer implements ClassFileTransformer {
        private final String outputPath;
        private final Set<HeapDumpStrategy> selectedStrategies;

        public LifecycleInstrumentingTransformer(String outputPath, Set<HeapDumpStrategy> selectedStrategies) {
            this.outputPath = outputPath;
            this.selectedStrategies = selectedStrategies;
        }

        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] originalByteCode) {
            // Find strategies that want to instrument this class
            List<HeapDumpStrategy> matchingStrategies = new ArrayList<>();
            for (HeapDumpStrategy strategy : this.selectedStrategies) {
                if (strategy.getTargetClassName().equals(className)) {
                    matchingStrategies.add(strategy);
                }
            }

            if (!matchingStrategies.isEmpty()) {
                return instrumentClass(className, originalByteCode, matchingStrategies);
            }
            return null;
        }

        private byte[] instrumentClass(String className, byte[] originalByteCode, List<HeapDumpStrategy> matchingStrategies) {
            ClassReader reader = new ClassReader(originalByteCode);
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);

            ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);

                    // Check if this method should be instrumented based on active strategies
                    for (HeapDumpStrategy strategy : matchingStrategies) {
                        if (shouldInstrumentMethod(strategy, name, descriptor)) {
                            System.out.println("HEAP_DUMP_AGENT: Instrumenting " + className + "#" + name + " for " + strategy.getName());
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
            return methodName.equals(strategy.getTargetMethodName()) && descriptor.equals(strategy.getTargetMethodDescriptor());
        }

        private MethodVisitor createInstrumentingMethodVisitor(MethodVisitor mv, HeapDumpStrategy strategy) {
            return new MethodVisitor(Opcodes.ASM9, mv) {
                @Override
                public void visitCode() {
                    super.visitCode();
                    // Inject call at the start of the method to call HeapDumpExecutor#invoke(outputDir, strategyName)
                    visitLdcInsn(outputPath);
                    visitLdcInsn(strategy.getName());
                    visitMethodInsn(Opcodes.INVOKESTATIC,
                        "org/gradle/profiler/heapdump/agent/HeapDumpExecutor",
                        "invoke",
                        "(Ljava/lang/String;Ljava/lang/String;)V",
                        false);
                }
            };
        }
    }
}
