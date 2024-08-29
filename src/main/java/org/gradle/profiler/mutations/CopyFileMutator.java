package org.gradle.profiler.mutations;

import com.google.common.base.Strings;
import com.typesafe.config.Config;
import org.apache.commons.io.FileUtils;
import org.gradle.profiler.BuildMutator;
import org.gradle.profiler.CompositeBuildMutator;
import org.gradle.profiler.ConfigUtil;
import org.gradle.profiler.ScenarioContext;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CopyFileMutator extends AbstractBuildMutator {

    private final File source;
    private final File target;

    public CopyFileMutator(File source, File target) {
        this.source = source;
        this.target = target;
    }

    @Override
    public void beforeScenario(ScenarioContext context) {
        try {
            System.out.println("Copying '" + source.getAbsolutePath() + "' to '" + target.getAbsolutePath() + "'");
            FileUtils.copyFile(source, target);
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
        public BuildMutator configure(String key, BuildMutatorConfiguratorSpec spec) {
            List<BuildMutator> mutators = new ArrayList<>();
            Object configRef = spec.getScenario().getAnyRef(key);
            // single item, or a list of items
            if (configRef instanceof Config) {
                mutators.add(createMutator((Config) configRef));
            } else {
                if (configRef instanceof List) {
                    List<? extends Config> configs = spec.getScenario().getConfigList(key);
                    configs.forEach(item -> mutators.add(createMutator(item)));
                } else {
                    throw new IllegalArgumentException("Expected copy-file, configuration to be a object or list of objects");
                }
            }
            return new CompositeBuildMutator(mutators);
        }
    }
    private static CopyFileMutator createMutator(Config config) {
        String source = ConfigUtil.string(config, "source", null);
        String target = ConfigUtil.string(config, "target", null);

        if (Strings.isNullOrEmpty(source) || Strings.isNullOrEmpty(target)) {
            throw new IllegalArgumentException("The `source` and `target` are required for copy-file");
        }
        return new CopyFileMutator(new File(source), new File(target));
    }
}
