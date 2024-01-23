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

        String ideType = buildConfiguration.getIdeType();
        String ideVersion = buildConfiguration.getIdeVersion();
        String ideBuildType = buildConfiguration.getIdeBuildType();

        Ide ide;
        if (ideType.equals("IC")) {
            ide = new IDEA(ideBuildType, ideVersion);
        } else if (ideType.equals("AI")) {
            ide = new AndroidStudio(ideVersion);
        } else {
            throw new IllegalArgumentException("Unknown IDE product was requested");
        }

        return ideProvider.provideIde(
            ide,
            buildConfiguration.getIdeHome().toAbsolutePath(),
            buildConfiguration.getIdeHome().resolve("downloads").toAbsolutePath()
        ).toPath();
    }
}
