package org.gradle.profiler.flamegraph;

import com.google.common.collect.ImmutableList;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Generates flame graphs based on JFR recordings.
 */
public class FlameGraphGenerator {
    public static final String FLAME_FILE_SUFFIX = "-flames.svg";
    public static final String ICICLE_FILE_SUFFIX = "-icicles.svg";

    private final FlameGraphTool flameGraphTool = new FlameGraphTool();

    public void generateGraphs(File flameGraphDirectory, List<Stacks> stackFiles) {
        if (!flameGraphTool.checkInstallation()) {
            return;
        }
        for (Stacks stacks : stackFiles) {
            generateFlameGraph(flameGraphDirectory, stacks);
            generateIcicleGraph(flameGraphDirectory, stacks);
        }
    }

    public void generateDifferentialGraphs(List<Stacks> stackFiles) {
        if (!flameGraphTool.checkInstallation()) {
            return;
        }
        for (Stacks stacks : stackFiles) {
            generateDifferentialFlameGraph(stacks);
            generateDifferentialIcicleGraph(stacks);
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
        flameGraphTool.generateFlameGraph(stacks.getFile(), new File(flameGraphDirectory, stacks.getFileBaseName() + FLAME_FILE_SUFFIX), options);
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
        flameGraphTool.generateFlameGraph(stacks.getFile(), new File(flameGraphDirectory, stacks.getFileBaseName() + ICICLE_FILE_SUFFIX), options);
    }

    private void generateDifferentialFlameGraph(Stacks stacks) {
        File stacksFile = stacks.getFile();
        EventType type = stacks.getType();
        DetailLevel level = stacks.getLevel();
        boolean negate = stacks.isNegate();
        File flames = new File(stacksFile.getParentFile(), stacks.getFileBaseName() + FlameGraphGenerator.FLAME_FILE_SUFFIX);
        ImmutableList.Builder<String> options = ImmutableList.builder();
        options
            .add("--title", type.getDisplayName() + (negate ? " Forward " : " Backward " + "Differential Flame Graph"))
            .add("--countname", type.getUnitOfMeasure())
            .addAll(level.getFlameGraphOptions());
        if (negate) {
            options.add("--negate");
        }

        flameGraphTool.generateFlameGraph(stacksFile, flames, options.build());
    }

    private void generateDifferentialIcicleGraph(Stacks stacks) {
        File stacksFile = stacks.getFile();
        EventType type = stacks.getType();
        DetailLevel level = stacks.getLevel();
        boolean negate = stacks.isNegate();
        File icicles = new File(stacksFile.getParentFile(), stacks.getFileBaseName() + FlameGraphGenerator.ICICLE_FILE_SUFFIX);
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

        flameGraphTool.generateFlameGraph(stacksFile, icicles, options.build());
    }
}
