package org.gradle.trace.buildops;

import org.gradle.api.NonNullApi;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.api.services.BuildServiceRegistry;
import org.gradle.execution.RunRootBuildWorkBuildOperationType;
import org.gradle.initialization.BuildRequestMetaData;
import org.gradle.internal.build.event.BuildEventListenerRegistryInternal;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.profiler.buildops.BuildOperationMeasurementKind;
import org.gradle.profiler.buildops.internal.InternalBuildOpMeasurementRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.inject.Inject;

// Used by org.gradle.profiler.buildops.BuildOperationInstrumentation via an injected initscript.
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
        Provider<BuildOperationDurationRecordingListener> listenerProvider = sharedServices.registerIfAbsent("time-to-first-task", BuildOperationDurationRecordingListener.class, spec -> {
            spec.getParameters().getCapturedBuildOperations().add(
                new InternalBuildOpMeasurementRequest(
                    outFile.toPath(),
                    RunRootBuildWorkBuildOperationType.class.getName(),
                    BuildOperationMeasurementKind.TIME_TO_FIRST_EXCLUSIVE
                )
            );
            spec.getParameters().getBuildStartTime().set(gradle.getServices().get(BuildRequestMetaData.class).getStartTime());
        });
        registry.onOperationCompletion(listenerProvider);
        return this;
    }

    public BuildOperationTrace measureBuildOperations(List<InternalBuildOpMeasurementRequest> capturedBuildOperations) {
        Provider<BuildOperationDurationRecordingListener> listenerProvider = sharedServices.registerIfAbsent("measure-build-operations", BuildOperationDurationRecordingListener.class, spec -> {
            spec.getParameters().getCapturedBuildOperations().set(capturedBuildOperations);
            spec.getParameters().getBuildStartTime().set(gradle.getServices().get(BuildRequestMetaData.class).getStartTime());
        });
        registry.onOperationCompletion(listenerProvider);
        return this;
    }

    private static void writeToFile(Path outputFile, long value, int count) throws IOException {
        // See `org.gradle.profiler.buildops.BuildOperationInstrumentation` for parsing
        String line = value + "," + count;
        Files.write(outputFile, Collections.singleton(line), StandardCharsets.UTF_8);
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
            writeToFile(getParameters().getOutputFile().getAsFile().get().toPath(), totalGcTime, 1);
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
            writeToFile(getParameters().getOutputFile().getAsFile().get().toPath(), cacheSizeInBytes.get(), cacheFileCount.get());
        }

        @Nullable
        private static File[] listBuildCacheDirectories(File cachesDir) {
            return cachesDir.listFiles(cacheDir -> cacheDir.isDirectory() && cacheDir.getName().startsWith("build-cache-"));
        }
    }

    public static abstract class BuildOperationDurationRecordingListener implements BuildService<BuildOperationDurationRecordingListener.Params>, BuildOperationListener, AutoCloseable {
        interface Params extends BuildServiceParameters {
            ListProperty<InternalBuildOpMeasurementRequest> getCapturedBuildOperations();

            Property<Long> getBuildStartTime();
        }

        private final List<BuildOperationCollector> collectors;

        @Inject
        public BuildOperationDurationRecordingListener(ObjectFactory objectFactory) {
            this.collectors = new ArrayList<>();
            for (InternalBuildOpMeasurementRequest request : getParameters().getCapturedBuildOperations().get()) {
                String operationType = request.getBuildOperationType();
                Class<?> detailsType;
                try {
                    detailsType = Class.forName(operationType + "$Details");
                } catch (ClassNotFoundException e) {
                    LOGGER.warn("Couldn't find Details subtype for operation type {}", operationType, e);
                    continue;
                }

                BuildOperationMeasurer measurer = BuildOperationMeasurer.createForKind(
                    request.getMeasurementKind(),
                    getParameters().getBuildStartTime().get()
                );

                collectors.add(new BuildOperationCollector(detailsType, request.getOutputFile(), measurer));
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
        private final Path outputFile;
        private final BuildOperationMeasurer measurer;
        private final AtomicInteger buildOperationCount = new AtomicInteger(0);

        private BuildOperationCollector(Class<?> detailsType, Path outputFile, BuildOperationMeasurer measurer) {
            this.detailsType = detailsType;
            this.outputFile = outputFile;
            this.measurer = measurer;
        }

        public void collect(Object details, OperationFinishEvent operationFinishEvent) {
            if (detailsType.isAssignableFrom(details.getClass())) {
                measurer.update(operationFinishEvent);
                buildOperationCount.incrementAndGet();
            }
        }

        public void write() throws IOException {
            Duration value = measurer.computeFinalValue();
            writeToFile(outputFile, value.toMillis(), buildOperationCount.get());
        }
    }
}
