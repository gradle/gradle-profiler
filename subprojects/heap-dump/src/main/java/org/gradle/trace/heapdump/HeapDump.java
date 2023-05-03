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
import javax.management.ObjectName;
import java.io.Closeable;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.util.Collections;

public class HeapDump {

    public HeapDump(GradleInternal gradle, File baseFile) {
        Provider<HeapDumpService> listenerProvider = gradle.getSharedServices().registerIfAbsent("heap-dump-at-end", HeapDumpService.class, spec -> {
            spec.getParameters().getBaseFile().set(baseFile.getAbsolutePath());
        });
        gradle.getServices().get(BuildEventsListenerRegistry.class).onTaskCompletion(listenerProvider);
    }

    interface Params extends BuildServiceParameters {
        Property<String> getBaseFile();
    }

    public static abstract class HeapDumpService implements OperationCompletionListener, Closeable, BuildService<Params> {
        static int counter = 0;

        @Override
        public void onFinish(FinishEvent event) {
            // Ignore
        }

        @Override
        public void close() {
            try {
                String dumpFileBase = getParameters().getBaseFile().get() + "-heap-" + (++counter);
                File hprofFile = new File(dumpFileBase + ".hprof");
                File jmapFile = new File(dumpFileBase + ".jmap");
                hprofFile.getParentFile().mkdirs();

                MBeanServer server = ManagementFactory.getPlatformMBeanServer();

                // Dump heap into hprof file
                HotSpotDiagnosticMXBean mxBean = ManagementFactory.newPlatformMXBeanProxy(server, "com.sun.management:type=HotSpotDiagnostic", HotSpotDiagnosticMXBean.class);
                mxBean.dumpHeap(hprofFile.getAbsolutePath(), true);

                // Dump class histogram to .jmap file
                // This should be the same output as running jmap -histo:live <pid>
                String histogram = (String) server.invoke(
                    new ObjectName("com.sun.management:type=DiagnosticCommand"),
                    "gcClassHistogram",
                    new Object[]{null},
                    new String[]{"[Ljava.lang.String;"});
                Files.write(jmapFile.toPath(), Collections.singleton(histogram));
            } catch (Exception e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
    }
}
