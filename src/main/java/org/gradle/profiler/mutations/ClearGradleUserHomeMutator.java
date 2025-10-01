package org.gradle.profiler.mutations;

import org.gradle.profiler.BuildMutator;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class ClearGradleUserHomeMutator extends AbstractScheduledMutator {
    private final File gradleUserHome;

    public ClearGradleUserHomeMutator(File gradleUserHome, Schedule schedule) {
        super(schedule);
        this.gradleUserHome = gradleUserHome;
    }

    @Override
    protected void executeOnSchedule() {
        System.out.printf("> Cleaning Gradle user home: %s%n", gradleUserHome.getAbsolutePath());
        if (!gradleUserHome.exists()) {
            throw new IllegalArgumentException(String.format(
                "Cannot delete Gradle user home directory (%s) since it does not exist",
                gradleUserHome
            ));
        }

        try {
            try (Stream<Path> contents = Files.list(gradleUserHome.toPath())) {
                contents
                    // Don't delete the wrapper dir, since this is where the Gradle distribution we are going to run is located
                    .filter(path -> !path.getFileName().toString().equals("wrapper"))
                    .forEach(path -> deleteFileOrDirectory(path.toFile()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static class Configurator extends AbstractScheduledMutator.Configurator {
        @Override
        protected BuildMutator newInstance(BuildMutatorConfiguratorSpec spec, String key, Schedule schedule) {
            return new ClearGradleUserHomeMutator(spec.getGradleUserHome(), schedule);
        }
    }
}
