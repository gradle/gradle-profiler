package org.gradle.trace.buildops;

import org.gradle.api.NonNullApi;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.api.services.BuildServiceRegistry;
import org.gradle.execution.RunRootBuildWorkBuildOperationType;
import org.gradle.internal.build.event.BuildEventListenerRegistryInternal;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.internal.operations.OperationStartEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

@SuppressWarnings("unused")
@NonNullApi
public class BuildOperationTrace {
    private static final Logger LOGGER = LoggerFactory.getLogger(BuildOperationTrace.class);

    private final GradleInternal gradle;
    private final BuildEventListenerRegistryInternal registry;
    private final BuildServiceRegistry sharedServices;

    public BuildOperationTrace(GradleInternal gradle) {
        this.gradle = gradle;
        this.registry = gradle.getServices().get(BuildEventListenerRegistryInternal.class);
        this.sharedServices = gradle.getSharedServices();
    }

    public BuildOperationTrace measureGarbageCollection(File outFile) {
        Provider<GcTimeCollectionService> listenerProvider = sharedServices.registerIfAbsent("gc-time", GcTimeCollectionService.class, spec -> {
            spec.getParameters().getOutputFile().set(outFile);
        });
        // Force the service to be instantiated, so we actually get a close() call at the end of the build
        registry.onOperationCompletion(listenerProvider);
        return this;
    }

    public BuildOperationTrace measureLocalBuildCache(File outFile) {
        gradle.settingsEvaluated(settings -> {
            Object cachePath = settings.getBuildCache().getLocal().getDirectory();
            File cacheDirectory = cachePath != null
                ? gradle.getServices().get(PathToFileResolver.class).resolve(cachePath)
                : null;
            Provider<LocalBuildCacheSizerService> listenerProvider = sharedServices.registerIfAbsent("local-build-cache-sizer", LocalBuildCacheSizerService.class, spec -> {
                spec.getParameters().getGradleUserHome().set(gradle.getGradleUserHomeDir());
                spec.getParameters().getCacheDirectory().set(cacheDirectory);
                spec.getParameters().getOutputFile().set(outFile);
            });
            // Force the service to be instantiated, so we actually get a close() call at the end of the build
            registry.onOperationCompletion(listenerProvider);
        });
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

    private static void writeToFile(File outputFile, long value, int count) throws IOException {
        // See `org.gradle.profiler.buildops.BuildOperationInstrumentation` for parsing
        try (PrintWriter writer = new PrintWriter(outputFile)) {
            writer.print(value);
            writer.print(",");
            writer.print(count);
            writer.println();
        }
    }

    public static abstract class GcTimeCollectionService implements BuildService<GcTimeCollectionService.Params>, BuildOperationListener, AutoCloseable {
        interface Params extends BuildServiceParameters {
            RegularFileProperty getOutputFile();
        }

        @Override
        public void started(BuildOperationDescriptor buildOperationDescriptor, OperationStartEvent operationStartEvent) {
            // Ignore, we are only interested in the callback to close()
        }

        @Override
        public void finished(BuildOperationDescriptor buildOperationDescriptor, OperationFinishEvent operationFinishEvent) {
            // Ignore, we are only interested in the callback to close()
        }

        @Override
        public void progress(OperationIdentifier operationIdentifier, OperationProgressEvent progressEvent) {
            // Ignore, we are only interested in the callback to close()
        }

        @Override
        public void close() throws Exception {
            long totalGcTime = ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(GarbageCollectorMXBean::getCollectionTime)
                .sum();
            writeToFile(getParameters().getOutputFile().getAsFile().get(), totalGcTime, 1);
        }
    }

    public static abstract class LocalBuildCacheSizerService implements BuildService<LocalBuildCacheSizerService.Params>, BuildOperationListener, AutoCloseable {
        interface Params extends BuildServiceParameters {
            DirectoryProperty getGradleUserHome();

            DirectoryProperty getCacheDirectory();

            RegularFileProperty getOutputFile();
        }

        @Inject
        protected abstract ProviderFactory getProviders();

        @Override
        public void started(BuildOperationDescriptor buildOperationDescriptor, OperationStartEvent operationStartEvent) {
            // Ignore, we are only interested in the callback to close()
        }

        @Override
        public void finished(BuildOperationDescriptor buildOperationDescriptor, OperationFinishEvent operationFinishEvent) {
            // Ignore, we are only interested in the callback to close()
        }

        @Override
        public void progress(OperationIdentifier operationIdentifier, OperationProgressEvent progressEvent) {
            // Ignore, we are only interested in the callback to close()
        }

        @Override
        public void close() throws Exception {
            AtomicInteger cacheFileCount = new AtomicInteger(0);
            AtomicLong cacheSizeInBytes = new AtomicLong(0);

            getParameters().getCacheDirectory()
                .getAsFile()
                .map(Stream::of)
                .orElse(getParameters().getGradleUserHome()
                    .dir("caches")
                    .map(Directory::getAsFile)
                    .map(LocalBuildCacheSizerService::listBuildCacheDirectories)
                    .map(Stream::of))
                .get()
                .map(File::listFiles)
                .filter(Objects::nonNull)
                .flatMap(Stream::of)
                .forEach(file -> {
                    cacheFileCount.incrementAndGet();
                    cacheSizeInBytes.addAndGet(file.length());
                });
            writeToFile(getParameters().getOutputFile().getAsFile().get(), cacheSizeInBytes.get(), cacheFileCount.get());
        }

        @Nullable
        private static File[] listBuildCacheDirectories(File cachesDir) {
            return cachesDir.listFiles(cacheDir -> cacheDir.isDirectory() && cacheDir.getName().startsWith("build-cache-"));
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
            writeToFile(getParameters().getOutputFile().getAsFile().get(), timeToFirstTask.longValue(), 1);
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
        private final AtomicInteger buildOperationCount = new AtomicInteger(0);

        public BuildOperationCollector(Class<?> detailsType, File outputFile) {
            this.detailsType = detailsType;
            this.outputFile = outputFile;
        }

        public void collect(Object details, OperationFinishEvent operationFinishEvent) {
            if (detailsType.isAssignableFrom(details.getClass())) {
                buildOperationTime.addAndGet(operationFinishEvent.getEndTime() - operationFinishEvent.getStartTime());
                buildOperationCount.incrementAndGet();
            }
        }

        public void write() throws IOException {
            writeToFile(outputFile, buildOperationTime.get(), buildOperationCount.get());
        }
    }
}
