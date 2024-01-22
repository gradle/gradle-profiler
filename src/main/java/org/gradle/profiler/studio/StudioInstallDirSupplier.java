package org.gradle.profiler.studio;

import org.gradle.profiler.InvocationSettings;
import org.gradle.profiler.ide.DefaultIdeProvider;
import org.gradle.profiler.ide.Ide;
import org.gradle.profiler.ide.IdeProvider;
import org.gradle.profiler.ide.idea.IDEA;
import org.gradle.profiler.ide.studio.AndroidStudio;
import org.gradle.profiler.studio.invoker.StudioGradleScenarioDefinition.StudioGradleBuildConfiguration;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Supplier;

public class StudioInstallDirSupplier implements Supplier<Path> {

    private final InvocationSettings invocationSettings;

    private final StudioGradleBuildConfiguration buildConfiguration;

    private final IdeProvider<Ide> ideProvider;

    public StudioInstallDirSupplier(InvocationSettings invocationSettings, StudioGradleBuildConfiguration buildConfiguration) {
        this.invocationSettings = invocationSettings;
        this.buildConfiguration = buildConfiguration;
        this.ideProvider = new DefaultIdeProvider();
    }

    @Override
    public Path get() {
        File studioInstallDir = invocationSettings.getStudioInstallDir();
        if (studioInstallDir != null) {
            return studioInstallDir.toPath();
        }

        Ide ide;
        if (buildConfiguration.getIdeType().equals("IC")) {
            ide = new IDEA(buildConfiguration.getIdeVersion());
        } else if (buildConfiguration.getIdeType().equals("AI")) {
            ide = new AndroidStudio(buildConfiguration.getIdeVersion());
        } else {
            throw new IllegalArgumentException("Unknown IDE product was requested");
        }

        return ideProvider.provideIde(ide, Paths.get("/Users/sopivalov/Projects/foo"), Paths.get("/Users/sopivalov/Projects/foo/downloads")).toPath();
    }
}
