package org.gradle.profiler.jfr;

import com.google.common.base.Joiner;
import org.gradle.profiler.asyncprofiler.AsyncProfilerController;
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
public class JfrFlameGraphGenerator {
    private final JfrToStacksConverter stacksConverter = new JfrToStacksConverter();
    private final FlameGraphTool flameGraphGenerator = new FlameGraphTool();

    public static class Stacks {
        private final File file;
        private final EventType type;
        private final DetailLevel level;
        private final String fileBaseName;

        public Stacks(File file, EventType type, DetailLevel level, String fileBaseName) {
            this.file = file;
            this.type = type;
            this.level = level;
            this.fileBaseName = fileBaseName;
        }

        public File getFile() {
            return file;
        }

        public EventType getType() {
            return type;
        }

        public DetailLevel getLevel() {
            return level;
        }

        public boolean isEmpty() {
            return file.length() == 0;
        }

        public String getFileBaseName() {
            return fileBaseName;
        }
    }

    public void generateStacksAndGraphs(File jfrFile, String outputBaseName) {
        List<Stacks> stackFiles = generateStacks(jfrFile, outputBaseName);
        generateGraphs(jfrFile.getParentFile(), stackFiles);
    }

    public List<Stacks> generateStacks(File jfrFile, String outputBaseName) {
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

        List<Stacks> stacks = new ArrayList<>();
        try {
            for (EventType type : EventType.values()) {
                for (DetailLevel level : DetailLevel.values()) {
                    String eventFileBaseName = Joiner.on("-").join(outputBaseName, type.getId(), level.name().toLowerCase(Locale.ROOT));
                    File stacksFile = generateStacks(jfrFile.getParentFile(), eventFileBaseName, recordings, type, level);
                    if (stacksFile != null) {
                        stacks.add(new Stacks(stacksFile, type, level, eventFileBaseName));
                    }
                }
            }
            return stacks;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void generateGraphs(File flameGraphDirectory, List<Stacks> stackFiles) {
        if (!flameGraphGenerator.checkInstallation()) {
            return;
        }
        for (Stacks stacks : stackFiles) {
            generateFlameGraph(flameGraphDirectory, stacks);
            generateIcicleGraph(flameGraphDirectory, stacks);
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
        File sanitizedStacks = new File(baseDir, eventFileBaseName + "-stacks.txt");
        level.getSanitizer().sanitize(stacks, sanitizedStacks);
        stacks.delete();
        return sanitizedStacks;
    }

    private void generateFlameGraph(File flameGraphDirectory, Stacks stacks) {
        if (stacks.isEmpty()) {
            return;
        }
        DetailLevel level = stacks.getLevel();
        EventType type = stacks.getType();
        List<String> options = new ArrayList<>();
        options.addAll(level.getFlameGraphOptions());
        options.addAll(Arrays.asList("--title", type.getDisplayName() + " Flame Graph", "--countname", type.getUnitOfMeasure(), "--colors", "java"));
        flameGraphGenerator.generateFlameGraph(stacks.getFile(), new File(flameGraphDirectory, stacks.getFileBaseName() + "-flames.svg"), options);
    }

    private void generateIcicleGraph(File flameGraphDirectory, Stacks stacks) {
        if (stacks.isEmpty()) {
            return;
        }
        DetailLevel level = stacks.getLevel();
        EventType type = stacks.getType();
        List<String> options = new ArrayList<>();
        options.addAll(level.getIcicleGraphOptions());
        options.addAll(Arrays.asList("--title", type.getDisplayName() + " Icicle Graph", "--countname", type.getUnitOfMeasure(), "--reverse", "--invert", "--colors", "java"));
        flameGraphGenerator.generateFlameGraph(stacks.getFile(), new File(flameGraphDirectory, stacks.getFileBaseName() + "-icicles.svg"), options);
    }

    public enum DetailLevel {
        RAW(
            true,
            true,
            Arrays.asList("--minwidth", "0.5"),
            Arrays.asList("--minwidth", "1"),
            FlameGraphSanitizer.raw()
        ),
        SIMPLIFIED(
            false,
            false,
            Arrays.asList("--minwidth", "1"),
            Arrays.asList("--minwidth", "2"),
            // TODO: Use the value configured in the config.
            FlameGraphSanitizer.simplified(new AsyncProfilerController.RemoveSystemThreads())
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
