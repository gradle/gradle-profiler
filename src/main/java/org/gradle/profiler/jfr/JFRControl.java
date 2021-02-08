package org.gradle.profiler.jfr;

import org.gradle.profiler.CommandExec;
import org.gradle.profiler.InstrumentingProfiler;

import java.io.File;
import java.io.IOException;

public class JFRControl implements InstrumentingProfiler.SnapshotCapturingProfilerController {

    private final JcmdRunner jcmd;
    private final JFRArgs jfrArgs;
    private final File jfrFile;

    public JFRControl(JFRArgs args, File jfrFile) {
        this.jcmd = new JcmdRunner();
        this.jfrArgs = args;
        this.jfrFile = jfrFile;
    }

    @Override
    public void startRecording(String pid) throws IOException, InterruptedException {
        jcmd.run(pid, "JFR.start", "name=profile", "settings=" + jfrArgs.getJfrSettings());
    }

    @Override
    public void stopRecording(String pid) throws IOException, InterruptedException {
        jcmd.run(pid, "JFR.stop", "name=profile", "filename=" + jfrFile.getAbsolutePath());
    }

    @Override
    public void captureSnapshot(String pid) {}

    @Override
    public void stopSession() throws IOException, InterruptedException {
        new JfrFlameGraphGenerator().generateGraphs(jfrFile);
        System.out.println("Wrote profiling data to " + jfrFile.getPath());
    }

    public String getName() {
        return "jfr";
    }

    private void run(String... commandLine) {
        new CommandExec().run(commandLine);
    }
}
