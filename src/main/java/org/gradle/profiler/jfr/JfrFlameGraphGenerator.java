package org.gradle.profiler.jfr;

import org.gradle.api.JavaVersion;
import org.gradle.profiler.OperatingSystem;
import org.gradle.profiler.fg.FlameGraphSanitizer;
import org.gradle.profiler.fg.FlameGraphTool;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.util.Collections.emptyList;

/**
 * Generates flame graphs based on JFR recordings.
 * <p>
 * TODO create flame graph diffs between profiled versions
 * TOOD make this work on Java 9+
 * TODO detect missing Perl instead of just excluding Windows
 */
class JfrFlameGraphGenerator {
    public void generateGraphs(File jfrRecording) {
        if (OperatingSystem.isWindows() || JavaVersion.current().isJava9Compatible()) {
            return;
        }
        for (EventType type : EventType.values()) {
            for (DetailLevel level : DetailLevel.values()) {
                try {
                    File stacks = generateStacks(jfrRecording, type, level);
                    generateFlameGraph(stacks, type, level);
                    generateIcicleGraph(stacks, type, level);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private File generateStacks(File jfrRecording, EventType type, DetailLevel level) throws IOException {
        File stacks = File.createTempFile("stacks", ".txt");
        List<String> options = new ArrayList<>();
        options.addAll(level.getStackConversionOptions());
        options.addAll(Arrays.asList("--event", type.id));
        stacksConverter.convertToStacks(jfrRecording, stacks, options);
        File baseDir = new File(jfrRecording.getParentFile(), jfrRecording.getName() + "-flamegraphs");
        File sanitizedStacks = stacksFileName(baseDir, type, level);
        sanitizedStacks.getParentFile().mkdirs();
        level.getSanitizer().sanitize(stacks, sanitizedStacks);
        stacks.delete();
        return sanitizedStacks;
    }

    private File stacksFileName(File baseDir, final EventType type, final DetailLevel level) {
        return new File(baseDir, type.id + "/" + level.name().toLowerCase() + "/stacks.txt");
    }

    private void generateFlameGraph(File stacks, EventType type, DetailLevel level) {
        if (stacks.length() == 0) {
            return;
        }
        File flames = new File(stacks.getParentFile(), "flames.svg");
        List<String> options = new ArrayList<>();
        options.addAll(level.getFlameGraphOptions());
        options.addAll(Arrays.asList("--title", type.displayName + " Flame Graph", "--countname", type.unitOfMeasure));
        flameGraphGenerator.generateFlameGraph(stacks, flames, options);
    }

    private void generateIcicleGraph(File stacks, EventType type, DetailLevel level) {
        if (stacks.length() == 0) {
            return;
        }
        File icicles = new File(stacks.getParentFile(), "icicles.svg");
        List<String> options = new ArrayList<>();
        options.addAll(level.getIcicleGraphOptions());
        options.addAll(Arrays.asList("--title", type.displayName + " Icicle Graph", "--countname", type.unitOfMeasure, "--reverse", "--invert", "--colors", "aqua"));
        flameGraphGenerator.generateFlameGraph(stacks, icicles, options);
    }

    private JfrToStacksConverter stacksConverter = new JfrToStacksConverter();
    private FlameGraphTool flameGraphGenerator = new FlameGraphTool();

    private enum EventType {
        CPU("cpu", "CPU", "samples"),
        ALLOCATION("allocation-tlab", "Allocation in new TLAB", "kB"),
        MONITOR_BLOCKED("monitor-blocked", "Java Monitor Blocked", "ms"),
        IO("io", "File and Socket IO", "ms");

        EventType(String id, String displayName, String unitOfMeasure) {
            this.unitOfMeasure = unitOfMeasure;
            this.displayName = displayName;
            this.id = id;
        }

        private final String id;
        private final String displayName;
        private final String unitOfMeasure;
    }

    private enum DetailLevel {
        RAW(
                emptyList(),
                Arrays.asList("--minwidth", "0.5"),
                Arrays.asList("--minwidth", "1"),
                new FlameGraphSanitizer(FlameGraphSanitizer.COLLAPSE_BUILD_SCRIPTS)
        ),
        SIMPLIFIED(
                Arrays.asList("--hide-arguments", "--ignore-line-numbers"),
                Arrays.asList("--minwidth", "1"),
                Arrays.asList("--minwidth", "2"),
                new FlameGraphSanitizer(FlameGraphSanitizer.COLLAPSE_BUILD_SCRIPTS, FlameGraphSanitizer.COLLAPSE_GRADLE_INFRASTRUCTURE, FlameGraphSanitizer.SIMPLE_NAMES)
        );

        private List<String> stackConversionOptions;
        private List<String> flameGraphOptions;
        private List<String> icicleGraphOptions;
        private FlameGraphSanitizer sanitizer;

        DetailLevel(List<String> stackConversionOptions, List<String> flameGraphOptions, List<String> icicleGraphOptions, FlameGraphSanitizer sanitizer) {
            this.stackConversionOptions = stackConversionOptions;
            this.flameGraphOptions = flameGraphOptions;
            this.icicleGraphOptions = icicleGraphOptions;
            this.sanitizer = sanitizer;
        }

        public List<String> getStackConversionOptions() {
            return stackConversionOptions;
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
