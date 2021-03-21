package org.gradle.profiler.flamegraph;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generates flame graphs based on JFR recordings.
 */
public class DifferentialFlameGraphGenerator {

    private final FlameGraphTool flameGraphGenerator = new FlameGraphTool();

    public void generateDifferentialGraphs(File baseOutputDir) throws IOException {
        try (Stream<Path> list = Files.list(baseOutputDir.toPath())) {
            List<Path> experiments = list
                .filter(Files::isDirectory)
                .collect(Collectors.toList());

            experiments.forEach(experiment -> {
                experiments.stream()
                    .filter(it -> !experiment.equals(it))
                    .forEach(baseline -> {
                        for (EventType type : EventType.values()) {
                            // Only create diffs for simplified stacks, diffs for raw stacks don't make much sense
                            DetailLevel level = DetailLevel.SIMPLIFIED;
                            File backwardDiff = generateDiff(experiment.toFile(), baseline.toFile(), type, level, false);
                            if (backwardDiff != null) {
                                generateDifferentialFlameGraph(backwardDiff, type, level, false);
                                generateDifferentialIcicleGraph(backwardDiff, type, level, false);
                            }

                            File forwardDiff = generateDiff(experiment.toFile(), baseline.toFile(), type, level, true);
                            if (forwardDiff != null) {
                                generateDifferentialFlameGraph(forwardDiff, type, level, true);
                                generateDifferentialIcicleGraph(forwardDiff, type, level, true);
                            }
                        }
                    });
            });
        }

    }

    private File generateDiff(File versionUnderTest, File baseline, final EventType type, final DetailLevel level, final boolean negate) {
        File underTestStacks = stacksFileName(versionUnderTest, type, level);
        File baselineStacks = stacksFileName(baseline, type, level);
        if (underTestStacks != null && baselineStacks != null) {
            final String underTestBasename = stacksBasename(underTestStacks, type, level);
            final String baselineTestBasename = stacksBasename(baselineStacks, type, level);
            final String commonPrefix = Strings.commonPrefix(underTestBasename, baselineTestBasename);
            final String commonSuffix = Strings.commonSuffix(underTestBasename, baselineTestBasename);
            final String diffBaseName = underTestBasename + "-vs-" + baselineTestBasename.substring(0, baselineTestBasename.length() - commonSuffix.length()).substring(commonPrefix.length()) + DifferentialFlameGraphGenerator.postFixFor(type, level) + "-" + (negate ? "forward-" : "backward-") + "diff";
            File diff = new File(underTestStacks.getParentFile(), "diffs/" + diffBaseName + "-stacks.txt");
            diff.getParentFile().mkdirs();
            if (negate) {
                flameGraphGenerator.generateDiff(underTestStacks, baselineStacks, diff);
            } else {
                flameGraphGenerator.generateDiff(baselineStacks, underTestStacks, diff);
            }

            return diff;
        }

        return null;
    }

    private static String stacksBasename(File underTestStacks, final EventType type, final DetailLevel level) {
        String postfix = postFixFor(type, level) + "-stacks.txt";
        String underTestStacksName = underTestStacks.getName();
        if (!underTestStacksName.endsWith(postfix)) {
            throw new RuntimeException("Stacks file '" + underTestStacks.getAbsolutePath() + "' doesn't follow the naming convention and does not end with " + postfix);
        }
        return underTestStacksName.substring(0, underTestStacksName.length() - postfix.length());
    }

    private static File stacksFileName(File baseDir, final EventType type, final DetailLevel level) {
        String suffix = postFixFor(type, level) + "-stacks.txt";
        File[] stackFiles = baseDir.listFiles((dir, name) -> name.endsWith(suffix));
        if (stackFiles.length == 1) {
            return stackFiles[0];
        }
        throw new RuntimeException("More than one matching stacks file found: " + Arrays.asList(stackFiles));
    }

    private static String postFixFor(final EventType type, final DetailLevel level) {
        return "-" + type.getId() + "-" + level.name().toLowerCase(Locale.ROOT);
    }

    private void generateDifferentialFlameGraph(File stacks, EventType type, DetailLevel level, final boolean negate) {
        File flames = new File(stacks.getParentFile(), stacks.getName().replace("-stacks.txt", "-flames.svg"));
        ImmutableList.Builder<String> options = ImmutableList.builder();
        options
            .add("--title", type.getDisplayName() + (negate ? " Forward " : " Backward " + "Differential Flame Graph"))
            .add("--countname", type.getUnitOfMeasure())
            .addAll(level.getFlameGraphOptions());
        if (negate) {
            options.add("--negate");
        }

        flameGraphGenerator.generateFlameGraph(stacks, flames, options.build());
    }

    private void generateDifferentialIcicleGraph(File stacks, EventType type, DetailLevel level, final boolean negate) {
        File icicles = new File(stacks.getParentFile(), stacks.getName().replace("-stacks.txt", "-icicles.svg"));
        ImmutableList.Builder<String> options = ImmutableList.builder();
        options
            .add("--title", type.getDisplayName() + (negate ? " Forward " : " Backward " + "Differential Icicle Graph"))
            .add("--countname", type.getUnitOfMeasure())
            .add("--reverse")
            .add("--invert")
            .addAll(level.getIcicleGraphOptions());
        if (negate) {
            options.add("--negate");
        }

        flameGraphGenerator.generateFlameGraph(stacks, icicles, options.build());
    }
}
