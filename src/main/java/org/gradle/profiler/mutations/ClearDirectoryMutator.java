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

    private final File target;
    private final List<String> keep;

    public ClearDirectoryMutator(File target, Schedule schedule, List<String> keep) {
        super(schedule);
        this.target = target;
        this.keep = keep;
    }

    @Override
    protected void executeOnSchedule() {
        System.out.printf("> Clearing directory: '%s'%n", target.getAbsolutePath());
        if (!target.exists()) {
            return;
        }

        try {
            try (Stream<Path> contents = Files.list(target.toPath())) {
                contents
                    .filter(path -> !keep.contains(path.getFileName().toString()))
                    .forEach(path -> deleteFileOrDirectory(path.toFile()));
            }
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
            File projectDir = spec.getProjectDir();
            return new ClearDirectoryMutator(resolveProjectFile(projectDir, target), schedule, keep);
        }
    }
}
