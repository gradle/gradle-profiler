package org.gradle.profiler.mutations;

import com.typesafe.config.Config;
import org.gradle.profiler.BuildMutator;
import org.gradle.profiler.CompositeBuildMutator;
import org.gradle.profiler.ConfigUtil;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ClearDirectoryMutator extends AbstractFileSystemMutator {

    private final String description;
    private final File target;
    private final List<String> keep;

    public ClearDirectoryMutator(String description, File target, Schedule schedule, List<String> keep) {
        super(schedule);
        this.description = description;
        this.target = target;
        this.keep = keep;
    }

    @Override
    protected void executeOnSchedule() {
        System.out.printf("> Clearing %s: '%s'%n", description, target.getAbsolutePath());
        if (!target.exists()) {
            return;
        }

        if (!target.isDirectory()) {
            throw new IllegalArgumentException(String.format("Cannot clear '%s' since it is not a directory", target.getAbsolutePath()));
        }

        try (Stream<Path> contents = Files.list(target.toPath())) {
            contents
                .filter(path -> !keep.contains(path.getFileName().toString()))
                .forEach(path -> deleteFileOrDirectory(path.toFile()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static class Configurator implements BuildMutatorConfigurator {

        @Override
        public BuildMutator configure(String key, BuildMutatorConfiguratorSpec spec) {
            return CompositeBuildMutator.from(ConfigUtil.configs(spec.getScenario(), key)
                .stream().map(config -> createMutator(spec, config))
                .collect(Collectors.toList()));
        }

        private static ClearDirectoryMutator createMutator(BuildMutatorConfiguratorSpec spec, Config config) {
            String target = ConfigUtil.string(config, "target");
            Schedule schedule = ConfigUtil.enumValue(config, "schedule", Schedule.class, Schedule.SCENARIO);
            List<String> keep = ConfigUtil.strings(config, "keep");
            FileRoot root = ConfigUtil.enumValue(config, "root", FileRoot.class, FileRoot.PROJECT);
            File projectDir = spec.getProjectDir();
            File gradleUserHome = spec.getGradleUserHome();
            return new ClearDirectoryMutator("directory", resolveFile(root, projectDir, gradleUserHome, target), schedule, keep);
        }
    }
}
