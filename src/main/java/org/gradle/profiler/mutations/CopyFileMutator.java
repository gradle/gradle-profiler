package org.gradle.profiler.mutations;

import com.google.common.base.Strings;
import com.google.common.io.Files;
import com.typesafe.config.Config;

import org.apache.commons.io.FileUtils;
import org.gradle.profiler.BuildMutator;
import org.gradle.profiler.CompositeBuildMutator;
import org.gradle.profiler.ConfigUtil;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.stream.Collectors;

public class CopyFileMutator extends AbstractFileSystemMutator {

    private final File source;
    private final File target;

    public CopyFileMutator(File source, File target, Schedule schedule) {
        super(schedule);
        this.source = source;
        this.target = target;
    }

    @Override
    protected void executeOnSchedule() {
        try {
            System.out.printf("> Copying '%s' to '%s'%n", source.getAbsolutePath(), target.getAbsolutePath());
            if (!target.exists()) {
                Files.createParentDirs(target);
            }
            if(source.isDirectory()) {
                FileUtils.copyDirectory(source, target);
            } else {
                Files.copy(source, target);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to copy '" + source.getAbsolutePath() + "' to '" + target.getAbsolutePath() + "'", e);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + source + " - " + target + ")";
    }

    public static class Configurator implements BuildMutatorConfigurator {

        @Override
        public BuildMutator configure(String key, BuildMutatorConfiguratorSpec spec) {
            return CompositeBuildMutator.from(ConfigUtil.configs(spec.getScenario(), key)
                .stream().map(config -> createMutator(spec, config))
                .collect(Collectors.toList()));
        }

        private static CopyFileMutator createMutator(BuildMutatorConfiguratorSpec spec, Config config) {
            String source = ConfigUtil.string(config, "source", null);
            String target = ConfigUtil.string(config, "target", null);
            Schedule schedule = ConfigUtil.enumValue(config, "schedule", Schedule.class, Schedule.SCENARIO);
            FileRoot root = ConfigUtil.enumValue(config, "root", FileRoot.class, FileRoot.PROJECT);

            if (Strings.isNullOrEmpty(source) || Strings.isNullOrEmpty(target)) {
                throw new IllegalArgumentException("The `source` and `target` are required for copy-file");
            }
            File projectDir = spec.getProjectDir();
            File gradleUserHome = spec.getGradleUserHome();
            return new CopyFileMutator(
                resolveFile(root, projectDir, gradleUserHome, source),
                resolveFile(root, projectDir, gradleUserHome, target),
                schedule
            );
        }
    }
}
