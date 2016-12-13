package org.gradle.profiler.fg;

import java.io.File;
import java.io.IOException;

public class FlameGraphGenerator
{
    private final File fgHomeDir;

    public FlameGraphGenerator( final File fgHomeDir ) {
        this.fgHomeDir = fgHomeDir;
    }

    public void generateFlameGraph( final File txtFile, final File fgFile ) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(
            fgHomeDir.getAbsolutePath() + File.separatorChar + "flamegraph.pl",
            txtFile.getAbsolutePath()
        );
        processBuilder.redirectOutput(fgFile);
        Process process = processBuilder.start();
        int result = process.waitFor();
        if (result != 0) {
            throw new RuntimeException("Unable to generate flame graph. Make sure your FlameGraph installation at " + fgHomeDir + " is correct.");
        }
    }
}
