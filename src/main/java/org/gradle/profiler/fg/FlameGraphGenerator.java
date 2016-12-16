package org.gradle.profiler.fg;

import org.gradle.profiler.CommandExec;

import java.io.File;
import java.io.IOException;

public class FlameGraphGenerator
{
    private final File fgHomeDir;

    public FlameGraphGenerator( final File fgHomeDir ) {
        this.fgHomeDir = fgHomeDir;
    }

    public void generateFlameGraph( final File txtFile, final File fgFile ) throws IOException, InterruptedException {
        new CommandExec().run(
            fgHomeDir.getAbsolutePath() + File.separatorChar + "flamegraph.pl",
            txtFile.getAbsolutePath()
        );
    }
}
