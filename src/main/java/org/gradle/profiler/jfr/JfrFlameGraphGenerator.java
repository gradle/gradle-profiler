package org.gradle.profiler.jfr;

import com.google.common.base.Joiner;
import org.gradle.profiler.flamegraph.FlameGraphSanitizer;
import org.gradle.profiler.flamegraph.FlameGraphTool;
import org.openjdk.jmc.common.item.IItemCollection;
import org.openjdk.jmc.flightrecorder.CouldNotLoadRecordingException;
import org.openjdk.jmc.flightrecorder.JfrLoaderToolkit;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static org.gradle.profiler.jfr.JfrToStacksConverter.EventType;
import static org.gradle.profiler.jfr.JfrToStacksConverter.Options;

/**
 * Generates flame graphs based on JFR recordings.
 * <p>
 * TODO create flame graph diffs between profiled versions
 */
class JfrFlameGraphGenerator {
    private final JfrToStacksConverter stacksConverter = new JfrToStacksConverter();
    private final FlameGraphTool flameGraphGenerator = new FlameGraphTool();

    public void generateGraphs(File jfrFile, String outputBaseName) {
        if (!flameGraphGenerator.checkInstallation()) {
            return;
        }

        Stream<File> jfrFiles = jfrFile.isDirectory()
            ? Stream.of(requireNonNull(jfrFile.listFiles((dir, name) -> name.endsWith(".jfr"))))
            : Stream.of(jfrFile);
        List<IItemCollection> recordings = jfrFiles
            .map(file -> {
                try {
                    return JfrLoaderToolkit.loadEvents(file);
                } catch (IOException | CouldNotLoadRecordingException e) {
                    throw new RuntimeException(e);
                }
            }).collect(Collectors.toList());

        try {
            generateGraphs(jfrFile.getParentFile(), outputBaseName, recordings);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void generateGraphs(File flameGraphDirectory, String outputBaseName, List<IItemCollection> recordings) throws IOException {
        for (EventType type : EventType.values()) {
            for (DetailLevel level : DetailLevel.values()) {
                String eventFileBaseName = Joiner.on("-").join(outputBaseName, type.getId(), level.name().toLowerCase(Locale.ROOT));
                File stacks = generateStacks(flameGraphDirectory, eventFileBaseName, recordings, type, level);
                if (stacks != null) {
                    generateFlameGraph(stacks, new File(flameGraphDirectory, eventFileBaseName + "-flames.svg"), type, level);
                    generateIcicleGraph(stacks, new File(flameGraphDirectory, eventFileBaseName + "-icicles.svg"), type, level);
                }
            }
        }
    }

    @Nullable
    private File generateStacks(File baseDir, String eventFileBaseName, List<IItemCollection> recordings, EventType type, DetailLevel level) throws IOException {
        File stacks = File.createTempFile("stacks", ".txt");
        stacksConverter.convertToStacks(recordings, stacks, new Options(type, level.isShowArguments(), level.isShowLineNumbers()));
        if (stacks.length() == 0) {
            stacks.delete();
            return null;
        }
        File sanitizedStacks = stacksFileName(baseDir, eventFileBaseName);
        level.getSanitizer().sanitize(stacks, sanitizedStacks);
        stacks.delete();
        return sanitizedStacks;
    }

    private File stacksFileName(File baseDir, String eventFileBaseName) {
        return new File(baseDir, eventFileBaseName + "-stacks.txt");
    }

    private void generateFlameGraph(File stacks, File flames, EventType type, DetailLevel level) {
        if (stacks.length() == 0) {
            return;
        }
        List<String> options = new ArrayList<>();
        options.addAll(level.getFlameGraphOptions());
        options.addAll(Arrays.asList("--title", type.getDisplayName() + " Flame Graph", "--countname", type.getUnitOfMeasure()));
        flameGraphGenerator.generateFlameGraph(stacks, flames, options);
    }

    private void generateIcicleGraph(File stacks, File icicles, EventType type, DetailLevel level) {
        if (stacks.length() == 0) {
            return;
        }
        List<String> options = new ArrayList<>();
        options.addAll(level.getIcicleGraphOptions());
        options.addAll(Arrays.asList("--title", type.getDisplayName() + " Icicle Graph", "--countname", type.getUnitOfMeasure(), "--reverse", "--invert", "--colors", "aqua"));
        flameGraphGenerator.generateFlameGraph(stacks, icicles, options);
    }

    private enum DetailLevel {
        RAW(
            true,
            true,
            Arrays.asList("--minwidth", "0.5"),
            Arrays.asList("--minwidth", "1"),
            new FlameGraphSanitizer(FlameGraphSanitizer.COLLAPSE_BUILD_SCRIPTS, FlameGraphSanitizer.NORMALIZE_LAMBDA_NAMES)
        ),
        SIMPLIFIED(
            false,
            false,
            Arrays.asList("--minwidth", "1"),
            Arrays.asList("--minwidth", "2"),
            new FlameGraphSanitizer(FlameGraphSanitizer.COLLAPSE_BUILD_SCRIPTS, FlameGraphSanitizer.COLLAPSE_GRADLE_INFRASTRUCTURE, FlameGraphSanitizer.SIMPLE_NAMES, FlameGraphSanitizer.NORMALIZE_LAMBDA_NAMES)
        );

        private final boolean showArguments;
        private final boolean showLineNumbers;
        private final List<String> flameGraphOptions;
        private final List<String> icicleGraphOptions;
        private final FlameGraphSanitizer sanitizer;

        DetailLevel(boolean showArguments, boolean showLineNumbers, List<String> flameGraphOptions, List<String> icicleGraphOptions, FlameGraphSanitizer sanitizer) {
            this.showArguments = showArguments;
            this.showLineNumbers = showLineNumbers;
            this.flameGraphOptions = flameGraphOptions;
            this.icicleGraphOptions = icicleGraphOptions;
            this.sanitizer = sanitizer;
        }

        public boolean isShowArguments() {
            return showArguments;
        }

        public boolean isShowLineNumbers() {
            return showLineNumbers;
        }

        public List<String> getFlameGraphOptions() {
            return flameGraphOptions;
        }

        public List<String> getIcicleGraphOptions() {
            return icicleGraphOptions;
        }

        public FlameGraphSanitizer getSanitizer() {
            return sanitizer;
        }

    }
}
