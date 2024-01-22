package org.gradle.profiler.studio;

import org.gradle.profiler.InvocationSettings;
import org.gradle.profiler.ide.IdeProviderCompatibilityLayerImpl;
import org.gradle.profiler.studio.invoker.StudioGradleScenarioDefinition.StudioGradleBuildConfiguration;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Supplier;

public class StudioInstallDirSupplier implements Supplier<Path> {

    private final InvocationSettings invocationSettings;

    private final StudioGradleBuildConfiguration buildConfiguration;

    private final IdeProviderCompatibilityLayerImpl ideProvider;

    public StudioInstallDirSupplier(InvocationSettings invocationSettings, StudioGradleBuildConfiguration buildConfiguration) {
        this.invocationSettings = invocationSettings;
        this.buildConfiguration = buildConfiguration;
        this.ideProvider = new IdeProviderCompatibilityLayerImpl();
    }

    @Override
    public Path get() {
        File studioInstallDir = invocationSettings.getStudioInstallDir();
        if (studioInstallDir != null) {
            return studioInstallDir.toPath();
        }

        return ideProvider.provideIde(
            buildConfiguration.getIdeType(),
            buildConfiguration.getIdeVersion(),
            Paths.get("/Users/sopivalov/Projects/foo"),
            Paths.get("/Users/sopivalov/Projects/foo/downloads")
        ).toPath();
    }
}
