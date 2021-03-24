package org.gradle.profiler.flamegraph;

import com.google.common.base.Joiner;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generates differential stacks.
 */
public class DifferentialStacksCreator {
    private final FlameGraphTool flameGraphTool = new FlameGraphTool();

    public List<Stacks> generateDifferentialStacks(File baseOutputDir) throws IOException {
        if (!flameGraphTool.checkInstallation()) {
            return Collections.emptyList();
        }
        List<Stacks> stacks = new ArrayList<>();
        try (Stream<Path> list = Files.list(baseOutputDir.toPath())) {
            List<Path> experiments = list
                .filter(Files::isDirectory)
                .collect(Collectors.toList());

            experiments.forEach(experiment -> experiments.stream()
                .filter(it -> !experiment.equals(it))
                .forEach(baseline -> {
                    for (EventType type : EventType.values()) {
                        // Only create diffs for simplified stacks, diffs for raw stacks don't make much sense
                        DetailLevel level = DetailLevel.SIMPLIFIED;
                        Stacks backwardDiff = generateDiff(experiment.toFile(), baseline.toFile(), type, level, false);
                        if (backwardDiff != null) {
                            stacks.add(backwardDiff);
                        }

                        Stacks forwardDiff = generateDiff(experiment.toFile(), baseline.toFile(), type, level, true);
                        if (forwardDiff != null) {
                            stacks.add(forwardDiff);
                        }
                    }
                }));
        }
        return stacks;
    }

    private Stacks generateDiff(File versionUnderTest, File baseline, final EventType type, final DetailLevel level, final boolean negate) {
        File underTestStacks = stacksFileName(versionUnderTest, type, level);
        File baselineStacks = stacksFileName(baseline, type, level);
        if (underTestStacks != null && baselineStacks != null) {
            final String underTestBasename = stacksBasename(underTestStacks, type, level);
            final String baselineTestBasename = stacksBasename(baselineStacks, type, level);
            String differentNamePart = computeDifferenceOfBaselineToCurrentName(underTestBasename, baselineTestBasename);
            final String diffBaseName = underTestBasename + "-vs-" + differentNamePart + Stacks.postFixFor(type, level) + "-" + (negate ? "forward-" : "backward-") + "diff";
            File diff = new File(underTestStacks.getParentFile(), "diffs/" + diffBaseName + Stacks.STACKS_FILE_SUFFIX);
            diff.getParentFile().mkdirs();
            if (negate) {
                flameGraphTool.generateDiff(underTestStacks, baselineStacks, diff);
            } else {
                flameGraphTool.generateDiff(baselineStacks, underTestStacks, diff);
            }

            return new Stacks(diff, type, level, diffBaseName, negate);
        }

        return null;
    }

    private String computeDifferenceOfBaselineToCurrentName(String underTestBasename, String baselineTestBasename) {
        List<String> underTestParts = Arrays.asList(underTestBasename.split("-"));
        Deque<String> remainderOfBaseline = new ArrayDeque<>(Arrays.asList(baselineTestBasename.split("-")));

        for (String underTestPart : underTestParts) {
            if (!remainderOfBaseline.isEmpty() && underTestPart.equals(remainderOfBaseline.getFirst())) {
                remainderOfBaseline.removeFirst();
            } else {
                break;
            }
        }

        Collections.reverse(underTestParts);
        for (String underTestPart : underTestParts) {
            if (!remainderOfBaseline.isEmpty() && underTestPart.equals(remainderOfBaseline.getLast())) {
                remainderOfBaseline.removeLast();
            } else {
                break;
            }
        }

        return Joiner.on("-").join(remainderOfBaseline);
    }

    private static String stacksBasename(File underTestStacks, final EventType type, final DetailLevel level) {
        String postfix = Stacks.postFixFor(type, level) + Stacks.STACKS_FILE_SUFFIX;
        String underTestStacksName = underTestStacks.getName();
        if (!underTestStacksName.endsWith(postfix)) {
            throw new RuntimeException("Stacks file '" + underTestStacks.getAbsolutePath() + "' doesn't follow the naming convention and does not end with " + postfix);
        }
        return underTestStacksName.substring(0, underTestStacksName.length() - postfix.length());
    }

    @Nullable
    private static File stacksFileName(File baseDir, final EventType type, final DetailLevel level) {
        String suffix = Stacks.postFixFor(type, level) + Stacks.STACKS_FILE_SUFFIX;
        File[] stackFiles = baseDir.listFiles((dir, name) -> name.endsWith(suffix));
        if (stackFiles == null || stackFiles.length == 0) {
            return null;
        }
        if (stackFiles.length == 1) {
            return stackFiles[0];
        }
        throw new RuntimeException("More than one matching stacks file found: " + Arrays.asList(stackFiles));
    }
}
