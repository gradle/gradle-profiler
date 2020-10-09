package org.gradle.trace.buildops;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.api.services.BuildServiceRegistry;
import org.gradle.execution.RunRootBuildWorkBuildOperationType;
import org.gradle.internal.build.event.BuildEventListenerRegistryInternal;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.tooling.events.FinishEvent;
import org.gradle.tooling.events.OperationCompletionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@SuppressWarnings("unused")
public class BuildOperationTrace {
    private static final Logger LOGGER = LoggerFactory.getLogger(BuildOperationTrace.class);

    private final BuildEventListenerRegistryInternal registry;
    private final BuildServiceRegistry sharedServices;

    public BuildOperationTrace(GradleInternal gradle) {
        this.registry = gradle.getServices().get(BuildEventListenerRegistryInternal.class);
        this.sharedServices = gradle.getSharedServices();
    }

    public BuildOperationTrace measureGarbageCollection(File outFile) {
        Provider<GcTimeCollectionService> listenerProvider = sharedServices.registerIfAbsent("gc-time", GcTimeCollectionService.class, spec -> {
            spec.getParameters().getOutputFile().set(outFile);
        });
        // Force the service to be instantiated so we actually get a close() call at the end of the build
        registry.onTaskCompletion(listenerProvider);
        return this;
    }

    public BuildOperationTrace measureConfigurationTime(File outFile) {
        Provider<TimeToFirstTaskRecordingListener> listenerProvider = sharedServices.registerIfAbsent("time-to-first-task", TimeToFirstTaskRecordingListener.class, spec -> {
            spec.getParameters().getOutputFile().set(outFile);
        });
        registry.onOperationCompletion(listenerProvider);
        return this;
    }

    public BuildOperationTrace measureBuildOperations(Map<String, File> capturedBuildOperations) {
        Provider<BuildOperationDurationRecordingListener> listenerProvider = sharedServices.registerIfAbsent("measure-build-operations", BuildOperationDurationRecordingListener.class, spec -> {
            spec.getParameters().getCapturedBuildOperations().set(new HashMap<>(capturedBuildOperations));
        });
        registry.onOperationCompletion(listenerProvider);
        return this;
    }

    private static void writeToFile(File outputFile, Object data) throws IOException {
        try (PrintWriter writer = new PrintWriter(outputFile)) {
            writer.println(data);
        }
    }

    public static abstract class GcTimeCollectionService implements BuildService<GcTimeCollectionService.Params>, OperationCompletionListener, AutoCloseable {
        interface Params extends BuildServiceParameters {
            RegularFileProperty getOutputFile();
        }

        @Override
        public void onFinish(FinishEvent event) {
            // Ignore, we are only interested in the callback to close()
        }

        @Override
        public void close() throws Exception {
            writeToFile(getParameters().getOutputFile().getAsFile().get(), ManagementFactory.getGarbageCollectorMXBeans().stream()
                .map(GarbageCollectorMXBean::getCollectionTime)
                .reduce(0L, Long::sum));
        }
    }

    public static abstract class TimeToFirstTaskRecordingListener implements BuildService<TimeToFirstTaskRecordingListener.Params>, BuildOperationListener, AutoCloseable {
        interface Params extends BuildServiceParameters {
            RegularFileProperty getOutputFile();
        }

        private final AtomicLong timeToFirstTask = new AtomicLong(0);

        @Override
        public void started(BuildOperationDescriptor buildOperationDescriptor, OperationStartEvent operationStartEvent) {
        }

        @Override
        public void progress(OperationIdentifier operationIdentifier, OperationProgressEvent operationProgressEvent) {
        }

        @Override
        public void finished(BuildOperationDescriptor buildOperationDescriptor, OperationFinishEvent operationFinishEvent) {
            if (buildOperationDescriptor.getDetails() instanceof RunRootBuildWorkBuildOperationType.Details) {
                RunRootBuildWorkBuildOperationType.Details details = (RunRootBuildWorkBuildOperationType.Details) buildOperationDescriptor.getDetails();
                long duration = operationFinishEvent.getStartTime() - details.getBuildStartTime();
                timeToFirstTask.set(duration);
            }
        }

        @Override
        public void close() throws Exception {
            writeToFile(getParameters().getOutputFile().getAsFile().get(), timeToFirstTask.longValue());
        }
    }

    public static abstract class BuildOperationDurationRecordingListener implements BuildService<BuildOperationDurationRecordingListener.Params>, BuildOperationListener, AutoCloseable {
        interface Params extends BuildServiceParameters {
            MapProperty<String, File> getCapturedBuildOperations();
        }

        private final List<BuildOperationCollector> collectors;

        public BuildOperationDurationRecordingListener() {
            this.collectors = new ArrayList<>();
            for (Map.Entry<String, File> entry : getParameters().getCapturedBuildOperations().get().entrySet()) {
                String operationType = entry.getKey();
                File outputFile = entry.getValue();
                Class<?> detailsType;
                try {
                    detailsType = Class.forName(operationType + "$Details");
                } catch (ClassNotFoundException e) {
                    LOGGER.warn("Couldn't find Details subtype for operation type {}", operationType, e);
                    continue;
                }

                collectors.add(new BuildOperationCollector(detailsType, outputFile));
            }
        }

        @Override
        public void started(BuildOperationDescriptor buildOperationDescriptor, OperationStartEvent operationStartEvent) {
        }

        @Override
        public void progress(OperationIdentifier operationIdentifier, OperationProgressEvent operationProgressEvent) {
        }

        @Override
        public void finished(BuildOperationDescriptor buildOperationDescriptor, OperationFinishEvent operationFinishEvent) {
            Object details = buildOperationDescriptor.getDetails();
            if (details == null) {
                return;
            }
            for (BuildOperationCollector collector : collectors) {
                collector.collect(details, operationFinishEvent);
            }
        }

        @Override
        public void close() throws IOException {
            for (BuildOperationCollector collector : collectors) {
                collector.write();
            }
        }
    }

    private static class BuildOperationCollector {
        private final Class<?> detailsType;
        private final File outputFile;
        private final AtomicLong buildOperationTime = new AtomicLong(0);

        public BuildOperationCollector(Class<?> detailsType, File outputFile) {
            this.detailsType = detailsType;
            this.outputFile = outputFile;
        }

        public void collect(Object details, OperationFinishEvent operationFinishEvent) {
            if (detailsType.isAssignableFrom(details.getClass())) {
                buildOperationTime.addAndGet(operationFinishEvent.getEndTime() - operationFinishEvent.getStartTime());
            }
        }

        public void write() throws IOException {
            writeToFile(outputFile, buildOperationTime.longValue());
        }
    }
}
