package org.gradle.profiler.flamegraph;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Generates flame graphs based on JFR recordings.
 * <p>
 * TODO create flame graph diffs between profiled versions
 */
public class FlameGraphGenerator {
    private final FlameGraphTool flameGraphGenerator = new FlameGraphTool();

    public void generateGraphs(File flameGraphDirectory, List<Stacks> stackFiles) {
        if (!flameGraphGenerator.checkInstallation()) {
            return;
        }
        for (Stacks stacks : stackFiles) {
            generateFlameGraph(flameGraphDirectory, stacks);
            generateIcicleGraph(flameGraphDirectory, stacks);
        }
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

}
