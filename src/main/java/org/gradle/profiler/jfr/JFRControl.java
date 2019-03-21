package org.gradle.profiler.jfr;

import org.gradle.profiler.CommandExec;
import org.gradle.profiler.InstrumentingProfiler;
import org.gradle.profiler.OperatingSystem;

import java.io.File;
import java.io.IOException;

public class JFRControl implements InstrumentingProfiler.SnapshotCapturingProfilerController {

    private final File jcmd;
    private final JFRArgs jfrArgs;
    private final File jfrFile;

    public JFRControl(JFRArgs args, File jfrFile) {
        File javaHome = new File(System.getProperty("java.home"));
        File jcmd = new File(javaHome, jcmdPath());
        if (!jcmd.isFile() && javaHome.getName().equals("jre")) {
            jcmd = new File(javaHome.getParentFile(), jcmdPath());
        }
        if (!jcmd.isFile()) {
            throw new RuntimeException("Could not find 'jcmd' executable for Java home directory " + javaHome+ ". Make sure your JAVA_HOME variable points to a JDK.");
        }
        this.jcmd = jcmd;
        this.jfrArgs = args;
        this.jfrFile = jfrFile;
    }

    private String jcmdPath() {
        return "bin/jcmd" + (OperatingSystem.isWindows() ? ".exe" : "");
    }

    @Override
    public void startRecording(String pid) throws IOException, InterruptedException {
        run(jcmd.getAbsolutePath(), pid, "JFR.start", "name=profile", "settings=" + jfrArgs.getJfrSettings());
    }

    @Override
    public void stopRecording(String pid) throws IOException, InterruptedException {
        run(jcmd.getAbsolutePath(), pid, "JFR.stop", "name=profile", "filename=" + jfrFile.getAbsolutePath());
    }

    @Override
    public void captureSnapshot(String pid) {
    }

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
