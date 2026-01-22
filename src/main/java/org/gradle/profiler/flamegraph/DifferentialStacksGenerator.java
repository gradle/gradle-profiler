package org.gradle.profiler.flamegraph;

import com.google.common.base.Joiner;

import javax.annotation.Nullable;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generates differential stacks.
 */
public class DifferentialStacksGenerator {

    public List<Stacks> generateDifferentialStacks(File baseOutputDir) throws IOException {
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
                generateDiff(underTestStacks, baselineStacks, diff);
            } else {
                generateDiff(baselineStacks, underTestStacks, diff);
            }

            return new Stacks(diff, type, level, diffBaseName, negate);
        }

        return null;
    }

    public void generateDiff(File versionUnderTest, File baseline, File diff) {
        try {
            Map<String, Long> parsedUnderTestStacks = readStacks(versionUnderTest);
            Map<String, Long> parsedBaselineStacks = readStacks(baseline);
            try (BufferedWriter writer = Files.newBufferedWriter(diff.toPath(), StandardCharsets.UTF_8)) {
                generateDiff(parsedUnderTestStacks, parsedBaselineStacks, stackLine -> {
                    try {
                        writer.write(stackLine);
                        writer.newLine();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }
        } catch (Exception e) {
            System.err.printf("Problem while creating differential stack file %s: %s%n", diff, e.getMessage());
        }
    }

    private Map<String, Long> readStacks(File file) throws IOException {
        try (Stream<String> lines = Files.lines(file.toPath(), StandardCharsets.UTF_8)) {
            Map<String, Long> result = new LinkedHashMap<>();
            lines.forEach(line -> {
                if (line.isEmpty()) {
                    return;
                }
                int lastSpace = line.lastIndexOf(" ");
                if (lastSpace == -1) {
                    return;
                }
                String stack = line.substring(0, lastSpace).trim();
                long count = Long.parseLong(line.substring(lastSpace + 1));
                result.compute(stack, (s, c) -> c == null ? count : c + count);
            });
            return result;
        }
    }

    private void generateDiff(Map<String, Long> from, Map<String, Long> to, Consumer<String> stackConsumer) {
        Set<String> allStacks = new HashSet<>();
        allStacks.addAll(from.keySet());
        allStacks.addAll(to.keySet());
        for (String stack : allStacks) {
            long countFrom = from.getOrDefault(stack, 0L);
            long countTo = to.getOrDefault(stack, 0L);
            stackConsumer.accept(stack + " " + countFrom + " " + countTo);
        }
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
