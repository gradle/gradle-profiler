package org.gradle.profiler.fg;

import org.gradle.profiler.CommandExec;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class FlameGraphGenerator
{
    private final File fgHomeDir;

    public FlameGraphGenerator( final File fgHomeDir ) {
        this.fgHomeDir = fgHomeDir;
    }

    public void generateFlameGraph( final File txtFile, final File fgFile ) throws IOException, InterruptedException {
        String output = new CommandExec().runAndCollectOutput(
                fgHomeDir.getAbsolutePath() + File.separatorChar + "flamegraph.pl",
                txtFile.getAbsolutePath()
        );
        try (FileOutputStream fos = new FileOutputStream(fgFile)) {
            fos.write(output.getBytes());
        }
    }
}
