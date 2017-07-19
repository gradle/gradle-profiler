package org.gradle.profiler.fg;

import org.gradle.profiler.CommandExec;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FlameGraphGenerator {
    private final File fgHomeDir;

    public FlameGraphGenerator(final File fgHomeDir) {
        this.fgHomeDir = fgHomeDir;
    }

    public void generateFlameGraph(final File txtFile, final File fgFile) throws IOException, InterruptedException {
        generateFlameGraph(txtFile, fgFile, false);
    }

    public void generateFlameGraph(final File txtFile, final File fgFile, boolean icicle) throws IOException, InterruptedException {
        CommandExec commandExec = new CommandExec();
        File fdCmd = new File(fgHomeDir, "flamegraph.pl");
        List<String> fgArgs = new ArrayList<>(Arrays.asList(fdCmd.getAbsolutePath(), txtFile.getAbsolutePath(), "--minwidth=0.5", "--color=java", "--hash"));
        if (icicle) {
            fgArgs.add("--reverse");
            fgArgs.add("--inverted");
        }
        commandExec.runAndCollectOutput(fgFile, fgArgs.toArray(new String[0]));
    }
}
