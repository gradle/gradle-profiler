package org.gradle.profiler.jfr;

import org.gradle.profiler.ProfilerController;

import java.io.File;
import java.io.IOException;

public class JFRControl implements ProfilerController {
    private final File jcmd;
    private final JFRArgs jfrArgs;

    public JFRControl(final JFRArgs args) {
        File javaHome = new File(System.getProperty("java.home"));
        File jcmd = new File(javaHome, "bin/jcmd");
        if (!jcmd.isFile() && javaHome.getName().equals("jre")) {
            jcmd = new File(javaHome.getParentFile(), "bin/jcmd");
        }
        if (!jcmd.isFile()) {
            throw new RuntimeException("Could not find 'jcmd' executable for Java home directory " + javaHome);
        }
        this.jcmd = jcmd;
        this.jfrArgs = args;
    }

    @Override
    public void start() throws IOException, InterruptedException {
        run(jcmd.getAbsolutePath(), jfrArgs.getPid(), "JFR.start", "name=profile", "settings=profile", "duration=0");
    }

    @Override
    public void stop() throws IOException, InterruptedException {
        String pid = jfrArgs.getPid();
        File recordingFile = jfrArgs.getRecordingFile();
        run(jcmd.getAbsolutePath(), pid, "JFR.stop", "name=profile", "filename=" + recordingFile.getAbsolutePath());
        System.out.println("Wrote profiling data to " + recordingFile.getPath());
    }

    private void run(String... commandLine) throws InterruptedException, IOException {
        ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
        Process process = processBuilder.start();
        int result = process.waitFor();
        if (result != 0) {
            throw new RuntimeException("Command " + commandLine[0] + " failed.");
        }
    }
}
