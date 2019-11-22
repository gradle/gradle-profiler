package org.gradle.profiler.mutations;

import org.apache.commons.io.FileUtils;
import org.gradle.profiler.BuildContext;
import org.gradle.profiler.BuildMutator;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;

public class ClearGradleUserHomeMutator extends AbstractBuildMutator {
    private final File gradleUserHome;

    public ClearGradleUserHomeMutator(File gradleUserHome) {
        this.gradleUserHome = gradleUserHome;
    }

    @Override
    public void beforeBuild(BuildContext context) {
        System.out.println(String.format("> Cleaning Gradle user home: %s", gradleUserHome.getAbsolutePath()));
        if (!gradleUserHome.exists()) {
            throw new IllegalArgumentException(String.format(
                "Cannot delete Gradle user home directory (%s) since it does not exist",
                gradleUserHome
            ));
        }
        try {
            Files.list(gradleUserHome.toPath())
                // Don't delete the wrapper dir, since this is where the Gradle distribution we are going to run is located
                .filter(it -> !it.getFileName().toString().equals("wrapper"))
                .forEach(file -> {
                    try {
                        FileUtils.forceDelete(file.toFile());
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static class Configurator extends AbstractBuildMutatorWithoutOptionsConfigurator {

        private final File gradleUserHome;

        public Configurator(File gradleUserHome) {
            this.gradleUserHome = gradleUserHome;
        }

        @Override
        BuildMutator createBuildMutator(File projectDir) {
            return new ClearGradleUserHomeMutator(gradleUserHome);
        }
    }
}
