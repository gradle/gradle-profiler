package org.gradle.profiler.studio.agent;

import jdk.internal.misc.Unsafe;
import org.gradle.profiler.client.protocol.Client;
import org.objectweb.asm.*;

import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Agent {
    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("PROFILER AGENT RUNNING");

        String[] args = agentArgs.split(",");
        int port = Integer.parseInt(args[0]);
        Path supportClasses = FileSystems.getDefault().getPath(args[1]);
        Client.INSTANCE.connect(port);
        System.out.println("* Connected to controller process");

        inst.addTransformer(new InstrumentingTransformer(supportClasses));
    }

    private static class InstrumentingTransformer implements ClassFileTransformer {
        static final Type INTERCEPTOR_TYPE = Type.getObjectType("org/gradle/profiler/studio/instrumented/Interceptor");
        static final Type DEFAULT_GRADLE_CONNECTOR = Type.getObjectType("org/gradle/tooling/internal/consumer/DefaultGradleConnector");

        private boolean supportClassesInjected;
        private final Path supportClassesJar;

        public InstrumentingTransformer(Path supportClassesJar) {
            this.supportClassesJar = supportClassesJar;
        }

        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] originalByteCode) {
            if (className.equals("org/gradle/tooling/internal/consumer/DefaultPhasedBuildActionExecuter")) {
                maybeInjectSupportClasses(loader);
                return instrumentBuildActionExecuter(originalByteCode);
            }
            if (className.equals(DEFAULT_GRADLE_CONNECTOR.getInternalName())) {
                maybeInjectSupportClasses(loader);
                return instrumentGradleConnector(originalByteCode);
            }
            return null;
        }

        private byte[] instrumentGradleConnector(byte[] originalByteCode) {
            System.out.println("* Instrumenting GradleConnector");

            Type projectConnection = Type.getObjectType("org/gradle/tooling/ProjectConnection");
            String connectMethodDescriptor = Type.getMethodDescriptor(projectConnection);
            String connectDescriptor = Type.getMethodDescriptor(Type.VOID_TYPE, DEFAULT_GRADLE_CONNECTOR);

            ClassReader reader = new ClassReader(originalByteCode);
            ClassWriter writer = new ClassWriter(0);
            ClassVisitor visitor = new ClassVisitor(Opcodes.ASM8, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if (name.equals("connect") && descriptor.equals(connectMethodDescriptor)) {
                        return new MethodVisitor(Opcodes.ASM8, methodVisitor) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                visitVarInsn(Opcodes.ALOAD, 0);
                                visitMethodInsn(Opcodes.INVOKESTATIC, INTERCEPTOR_TYPE.getInternalName(), "onConnect", connectDescriptor, false);
                            }

                            @Override
                            public void visitMaxs(int maxStack, int maxLocals) {
                                super.visitMaxs(Math.max(1, maxStack), maxLocals);
                            }
                        };
                    }
                    return methodVisitor;
                }
            };
            reader.accept(visitor, 0);

            return writer.toByteArray();
        }

        private byte[] instrumentBuildActionExecuter(byte[] originalByteCode) {
            System.out.println("* Instrumenting BuildExecuter");

            Type resultHandler = Type.getObjectType("org/gradle/tooling/ResultHandler");
            Type abstractHandlerType = Type.getObjectType("org/gradle/tooling/internal/consumer/AbstractLongRunningOperation");
            String runMethodDescriptor = Type.getMethodDescriptor(Type.VOID_TYPE, resultHandler);
            String startOperationDescriptor = Type.getMethodDescriptor(resultHandler, abstractHandlerType, resultHandler);

            ClassReader reader = new ClassReader(originalByteCode);
            ClassWriter writer = new ClassWriter(0);
            ClassVisitor visitor = new ClassVisitor(Opcodes.ASM8, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if (name.equals("run") && descriptor.equals(runMethodDescriptor)) {
                        return new MethodVisitor(Opcodes.ASM8, methodVisitor) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                visitVarInsn(Opcodes.ALOAD, 0);
                                visitVarInsn(Opcodes.ALOAD, 1);
                                visitMethodInsn(Opcodes.INVOKESTATIC, INTERCEPTOR_TYPE.getInternalName(), "onStartOperation", startOperationDescriptor, false);
                                visitVarInsn(Opcodes.ASTORE, 1);
                            }

                            @Override
                            public void visitMaxs(int maxStack, int maxLocals) {
                                super.visitMaxs(Math.max(2, maxStack), maxLocals);
                            }
                        };
                    }
                    return methodVisitor;
                }
            };
            reader.accept(visitor, 0);

            return writer.toByteArray();
        }

        private synchronized void maybeInjectSupportClasses(ClassLoader loader) {
            if (supportClassesInjected) {
                return;
            }

            System.out.println("* Injecting support classes from " + supportClassesJar);

            try {
                try (InputStream jarInputStream = Files.newInputStream(supportClassesJar)) {
                    ZipInputStream inputStream = new ZipInputStream(jarInputStream);
                    while (true) {
                        ZipEntry entry = inputStream.getNextEntry();
                        if (entry == null) {
                            break;
                        }
                        if (entry.getName().endsWith(".class")) {
                            injectClass(loader, entry.getName().substring(0, entry.getName().length() - 6), entry.getSize(), inputStream);
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException("Could not inject support classes.", e);
            }

            supportClassesInjected = true;
        }

        private static void injectClass(ClassLoader loader, String internalName, long length, InputStream inputStream) throws IOException {
            byte[] bytecode = new byte[(int) length];
            int remaining = bytecode.length;
            while (remaining > 0) {
                int nread = inputStream.read(bytecode, bytecode.length - remaining, remaining);
                if (nread < 0) {
                    break;
                }
                remaining -= nread;
            }
            Unsafe.getUnsafe().defineClass(internalName, bytecode, 0, bytecode.length, loader, null);
        }
    }
}
