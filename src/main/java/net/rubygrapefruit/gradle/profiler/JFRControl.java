package net.rubygrapefruit.gradle.profiler;

import java.io.File;
import java.io.IOException;

class JFRControl {
    private final File jcmd;

    public JFRControl() {
        File javaHome = new File(System.getProperty("java.home"));
        File jcmd = new File(javaHome, "bin/jcmd");
        if (!jcmd.isFile() && javaHome.getName().equals("jre")) {
            jcmd = new File(javaHome.getParentFile(), "bin/jcmd");
        }
        if (!jcmd.isFile()) {
            throw new RuntimeException("Could not find 'jcmd' executable for Java home directory " + javaHome);
        }
        this.jcmd = jcmd;
    }

    public void start(String pid) throws IOException, InterruptedException {
        run(jcmd.getAbsolutePath(), pid, "JFR.start", "name=profile", "settings=profile", "duration=0");
    }

    public void stop(String pid, File recordingFile) throws IOException, InterruptedException {
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
