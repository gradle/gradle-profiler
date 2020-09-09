package org.gradle.trace.buildops;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.provider.Property;
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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@SuppressWarnings("unused")
public class BuildOperationTrace {

    private final BuildEventListenerRegistryInternal registry;
    private final BuildServiceRegistry sharedServices;
    private final Map<String, File> capturedBuildOperations = new LinkedHashMap<>();

    public BuildOperationTrace(GradleInternal gradle) {
        registry = gradle.getServices().get(BuildEventListenerRegistryInternal.class);
        sharedServices = gradle.getSharedServices();
    }

    public BuildOperationTrace measureConfigurationTime(File outFile) {
        Provider<TimeToFirstTaskRecordingListener> listenerProvider = sharedServices.registerIfAbsent("time-to-first-task", TimeToFirstTaskRecordingListener.class, spec -> {
            spec.getParameters().getOutputFile().set(outFile);
        });
        registry.onOperationCompletion(listenerProvider);
        return this;
    }

    public BuildOperationTrace measureBuildOperation(String buildOperationName, File outFile) {
        Provider<BuildOperationDurationRecordingListener> listenerProvider = sharedServices.registerIfAbsent("measure-build-operations", BuildOperationDurationRecordingListener.class, spec -> {
            spec.getParameters().getCapturedBuildOperations().set(capturedBuildOperations);
        });
        registry.onOperationCompletion(listenerProvider);
        capturedBuildOperations.put(buildOperationName, outFile);
        return this;
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
        public void close() throws IOException {
            Files.write(getParameters().getOutputFile().get().getAsFile().toPath(), String.valueOf(timeToFirstTask.longValue()).getBytes(StandardCharsets.UTF_8));
        }
    }

    public static abstract class BuildOperationDurationRecordingListener implements BuildService<BuildOperationDurationRecordingListener.Params>, BuildOperationListener, AutoCloseable {
        interface Params extends BuildServiceParameters {
            Property<Map<String, File>> getCapturedBuildOperations();
        }

        private final List<BuildOperationCollector> collectors;

        public BuildOperationDurationRecordingListener() throws ClassNotFoundException {
            this.collectors = new ArrayList<>();
            for (Map.Entry<String, File> entry : getParameters().getCapturedBuildOperations().get().entrySet()) {
                String operationType = entry.getKey();
                File outputFile = entry.getValue();
                collectors.add(new BuildOperationCollector(Class.forName(operationType + "$Details"), outputFile));
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
            Files.write(outputFile.toPath(), String.valueOf(buildOperationTime.longValue()).getBytes(StandardCharsets.UTF_8));
        }
    }
}
