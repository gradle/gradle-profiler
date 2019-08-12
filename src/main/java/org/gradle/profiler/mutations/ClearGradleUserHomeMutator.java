package org.gradle.profiler.mutations;

import com.typesafe.config.Config;
import org.apache.commons.io.FileUtils;
import org.gradle.profiler.BuildMutator;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.function.Supplier;

public class ClearGradleUserHomeMutator extends AbstractBuildMutator {
    private final File gradleUserHome;

    public ClearGradleUserHomeMutator(File gradleUserHome) {
        this.gradleUserHome = gradleUserHome;
    }

    @Override
    public void beforeBuild() {
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

    public static class Configurator implements BuildMutatorConfigurator {

        private final File gradleUserHome;

        public Configurator(File gradleUserHome) {
            this.gradleUserHome = gradleUserHome;
        }

        @Override
        public Supplier<BuildMutator> configure(Config scenario, String scenarioName, File projectDir, String key) {
            boolean enabled = scenario.getBoolean(key);
            return () -> enabled ? new ClearGradleUserHomeMutator(gradleUserHome) : BuildMutator.NOOP;
        }
    }
}
