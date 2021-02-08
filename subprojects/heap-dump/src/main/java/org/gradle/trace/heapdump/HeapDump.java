package org.gradle.trace.heapdump;

import com.sun.management.HotSpotDiagnosticMXBean;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.build.event.BuildEventsListenerRegistry;
import org.gradle.internal.UncheckedException;
import org.gradle.tooling.events.FinishEvent;
import org.gradle.tooling.events.OperationCompletionListener;

import javax.management.MBeanServer;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;

public class HeapDump {

    public HeapDump(GradleInternal gradle, File baseFile) {
        Provider<HeapDumpService> listenerProvider =
                gradle.getSharedServices()
                        .registerIfAbsent(
                                "heap-dump-at-end",
                                HeapDumpService.class,
                                spec -> {
                                    spec.getParameters()
                                            .getBaseFile()
                                            .set(baseFile.getAbsolutePath());
                                });
        gradle.getServices()
                .get(BuildEventsListenerRegistry.class)
                .onTaskCompletion(listenerProvider);
    }

    interface Params extends BuildServiceParameters {
        Property<String> getBaseFile();
    }

    public abstract static class HeapDumpService
            implements OperationCompletionListener, Closeable, BuildService<Params> {
        static int counter = 0;

        @Override
        public void onFinish(FinishEvent event) {
            // Ignore
        }

        @Override
        public void close() {
            try {
                File dumpFile =
                        new File(
                                getParameters().getBaseFile().get()
                                        + "-heap-"
                                        + (++counter)
                                        + ".hprof");
                dumpFile.getParentFile().mkdirs();
                MBeanServer server = ManagementFactory.getPlatformMBeanServer();
                HotSpotDiagnosticMXBean mxBean =
                        ManagementFactory.newPlatformMXBeanProxy(
                                server,
                                "com.sun.management:type=HotSpotDiagnostic",
                                HotSpotDiagnosticMXBean.class);
                mxBean.dumpHeap(dumpFile.getAbsolutePath(), true);
            } catch (IOException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
    }
}
