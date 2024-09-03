package org.gradle.profiler.mutations;

import com.google.common.base.Strings;
import com.google.common.io.Files;
import com.typesafe.config.Config;
import org.gradle.profiler.ConfigUtil;
import org.gradle.profiler.ScenarioContext;

import java.io.File;
import java.io.IOException;

public class CopyFileMutator extends AbstractFileSystemMutator {

    private final File source;
    private final File target;

    public CopyFileMutator(File source, File target) {
        this.source = source;
        this.target = target;
    }

    @SuppressWarnings("UnstableApiUsage")
    @Override
    public void beforeScenario(ScenarioContext context) {
        try {
            System.out.println("Copying '" + source.getAbsolutePath() + "' to '" + target.getAbsolutePath() + "'");
            if (!target.exists()) {
                Files.createParentDirs(target);
            }
            Files.copy(source, target);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to copy '" + source.getAbsolutePath() + "' to '" + target.getAbsolutePath() + "'", e);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + source + " - " + target + ")";
    }

    public static class Configurator implements BuildMutatorConfigurator {

        @Override
        public CopyFileMutator configure(String key, BuildMutatorConfiguratorSpec spec) {
            Config config = spec.getScenario().getConfig(key);
            String source = ConfigUtil.string(config, "source", null);
            String target = ConfigUtil.string(config, "target", null);

            if (Strings.isNullOrEmpty(source) || Strings.isNullOrEmpty(target)) {
                throw new IllegalArgumentException("The `source` and `target` are required for copy-file");
            }
            File projectDir = spec.getProjectDir();
            return new CopyFileMutator(resolveProjectFile(projectDir, source), resolveProjectFile(projectDir, target));
        }
    }
}
