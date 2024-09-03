package org.gradle.profiler.mutations;

import com.typesafe.config.Config;
import org.apache.commons.io.FileUtils;
import org.gradle.profiler.ConfigUtil;
import org.gradle.profiler.ScenarioContext;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;

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
        public DeleteFileMutator configure(String key, BuildMutatorConfiguratorSpec spec) {
            Config config = spec.getScenario().getConfig(key);
            String target = ConfigUtil.string(config, "target");
            File projectDir = spec.getProjectDir();
            return new DeleteFileMutator(resolveProjectFile(projectDir, target));
        }
    }
}
