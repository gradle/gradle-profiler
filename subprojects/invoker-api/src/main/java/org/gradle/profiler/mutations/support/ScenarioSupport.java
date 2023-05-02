package org.gradle.profiler.mutations.support;

import com.typesafe.config.Config;
import org.gradle.profiler.ConfigUtil;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class ScenarioSupport {

    public static List<File> sourceFiles(Config config, String scenarioName, File projectDir, String key) {
        return ConfigUtil.strings(config, key).stream()
            .filter(Objects::nonNull)
            .map(fileName -> new File(projectDir, fileName))
            .peek(file -> {
                if (!file.isFile()) {
                    throw new IllegalArgumentException("Source file " + file.getName() + " specified for scenario " + scenarioName + " does not exist.");
                }
            })
            .collect(Collectors.toList());
    }

}
