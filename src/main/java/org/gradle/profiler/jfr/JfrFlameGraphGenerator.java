package org.gradle.profiler.jfr;

import org.gradle.profiler.fg.FlameGraphSanitizer;
import org.gradle.profiler.fg.FlameGraphTool;
import org.openjdk.jmc.common.item.IItemCollection;
import org.openjdk.jmc.flightrecorder.JfrLoaderToolkit;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.gradle.profiler.jfr.JfrToStacksConverter.EventType;
import static org.gradle.profiler.jfr.JfrToStacksConverter.Options;

/**
 * Generates flame graphs based on JFR recordings.
 * <p>
 * TODO create flame graph diffs between profiled versions
 */
class JfrFlameGraphGenerator {
    private JfrToStacksConverter stacksConverter = new JfrToStacksConverter();
    private FlameGraphTool flameGraphGenerator = new FlameGraphTool();

    public void generateGraphs(File jfrFile) {
        if (!flameGraphGenerator.checkInstallation()) {
            return;
        }
        try {
            IItemCollection events = JfrLoaderToolkit.loadEvents(jfrFile);
            generateGraphs(jfrFile, events);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void generateGraphs(File jfrFile, IItemCollection recording) throws IOException {
        File flamegraphDir = new File(jfrFile.getParentFile(), jfrFile.getName() + "-flamegraphs");
        for (EventType type : EventType.values()) {
            for (DetailLevel level : DetailLevel.values()) {
                File stacks = generateStacks(flamegraphDir, recording, type, level);
                generateFlameGraph(stacks, type, level);
                generateIcicleGraph(stacks, type, level);
            }
        }
    }

    private File generateStacks(File baseDir, IItemCollection recording, EventType type, DetailLevel level) throws IOException {
        File stacks = File.createTempFile("stacks", ".txt");
        stacksConverter.convertToStacks(recording, stacks, new Options(type, level.isShowArguments(), level.isShowLineNumbers()));
        File sanitizedStacks = stacksFileName(baseDir, type, level);
        level.getSanitizer().sanitize(stacks, sanitizedStacks);
        stacks.delete();
        return sanitizedStacks;
    }

    private File stacksFileName(File baseDir, final EventType type, final DetailLevel level) {
        return new File(baseDir, type.getId() + "/" + level.name().toLowerCase() + "/stacks.txt");
    }

    private void generateFlameGraph(File stacks, EventType type, DetailLevel level) {
        if (stacks.length() == 0) {
            return;
        }
        File flames = new File(stacks.getParentFile(), "flames.svg");
        List<String> options = new ArrayList<>();
        options.addAll(level.getFlameGraphOptions());
        options.addAll(Arrays.asList("--title", type.getDisplayName() + " Flame Graph", "--countname", type.getUnitOfMeasure()));
        flameGraphGenerator.generateFlameGraph(stacks, flames, options);
    }

    private void generateIcicleGraph(File stacks, EventType type, DetailLevel level) {
        if (stacks.length() == 0) {
            return;
        }
        File icicles = new File(stacks.getParentFile(), "icicles.svg");
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
            new FlameGraphSanitizer(FlameGraphSanitizer.COLLAPSE_BUILD_SCRIPTS)
        ),
        SIMPLIFIED(
            false,
            false,
            Arrays.asList("--minwidth", "1"),
            Arrays.asList("--minwidth", "2"),
            new FlameGraphSanitizer(FlameGraphSanitizer.COLLAPSE_BUILD_SCRIPTS, FlameGraphSanitizer.COLLAPSE_GRADLE_INFRASTRUCTURE, FlameGraphSanitizer.SIMPLE_NAMES)
        );

        private final boolean showArguments;
        private final boolean showLineNumbers;
        private List<String> flameGraphOptions;
        private List<String> icicleGraphOptions;
        private FlameGraphSanitizer sanitizer;

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
