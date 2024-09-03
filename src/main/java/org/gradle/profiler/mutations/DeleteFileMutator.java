package org.gradle.profiler.mutations;

import com.typesafe.config.Config;
import org.apache.commons.io.FileUtils;
import org.gradle.profiler.BuildMutator;
import org.gradle.profiler.CompositeBuildMutator;
import org.gradle.profiler.ConfigUtil;
import org.gradle.profiler.ScenarioContext;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.stream.Collectors;

public class DeleteFileMutator extends AbstractFileSystemMutator {

    private final File target;

    public DeleteFileMutator(File target) {
        this.target = target;
    }

    @Override
    public void beforeScenario(ScenarioContext context) {
        System.out.println("Removing file: '" + target.getAbsolutePath() + "'");
        try {
            if (target.exists()) {
                FileUtils.forceDelete(target);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete '" + target.getAbsolutePath() + "'", e);
        }
    }

    public static class Configurator implements BuildMutatorConfigurator {

        @Override
        public BuildMutator configure(String key, BuildMutatorConfiguratorSpec spec) {
            return CompositeBuildMutator.from(ConfigUtil.configs(spec.getScenario(), key)
                .stream().map(config -> createMutator(spec, config))
                .collect(Collectors.toList()));
        }

        private static DeleteFileMutator createMutator(BuildMutatorConfiguratorSpec spec, Config config) {
            String target = ConfigUtil.string(config, "target");
            File projectDir = spec.getProjectDir();
            return new DeleteFileMutator(resolveProjectFile(projectDir, target));
        }
    }
}
