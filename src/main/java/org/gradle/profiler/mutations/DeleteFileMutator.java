package org.gradle.profiler.mutations;

import org.apache.commons.io.FileUtils;
import org.gradle.profiler.BuildMutator;
import org.gradle.profiler.ConfigUtil;
import org.gradle.profiler.ScenarioContext;

import java.io.File;
import java.io.IOException;

public class DeleteFileMutator extends AbstractBuildMutator {

    private final File targetFile;

    public DeleteFileMutator(File targetFile) {
        this.targetFile = targetFile;
    }

    @Override
    public void beforeScenario(ScenarioContext context) {
        System.out.println("Removing file: '" + targetFile.getAbsolutePath() + "'");
        try {
            if (targetFile.exists()) {
                FileUtils.forceDelete(targetFile);
            }
        } catch (
        IOException e) {
            throw new IllegalStateException("Failed to delete '" + targetFile.getAbsolutePath() + "'", e);
        }
    }

    public static class Configurator implements BuildMutatorConfigurator {

        @Override
        public BuildMutator configure(String key, BuildMutatorConfiguratorSpec spec) {
            String target = ConfigUtil.string(spec.getScenario(), key);
            return new DeleteFileMutator(new File(target));
        }
    }
}
