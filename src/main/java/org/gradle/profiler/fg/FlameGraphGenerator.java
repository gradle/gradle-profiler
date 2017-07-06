package org.gradle.profiler.fg;

import org.gradle.profiler.CommandExec;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

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
        if (icicle) {
            commandExec.runAndCollectOutput(fgFile, fdCmd.getAbsolutePath(), txtFile.getAbsolutePath(), "--reverse", "--inverted");
        } else {
            commandExec.runAndCollectOutput(fgFile, fdCmd.getAbsolutePath(), txtFile.getAbsolutePath());
        }
    }
}
