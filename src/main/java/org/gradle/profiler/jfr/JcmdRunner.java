package org.gradle.profiler.jfr;

import org.gradle.profiler.CommandExec;
import org.gradle.profiler.OperatingSystem;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class JcmdRunner {
    private final File jcmd;

    public JcmdRunner() {
        File javaHome = new File(System.getProperty("java.home"));
        File jcmd = new File(javaHome, jcmdPath());
        if (!jcmd.isFile() && javaHome.getName().equals("jre")) {
            jcmd = new File(javaHome.getParentFile(), jcmdPath());
        }
        if (!jcmd.isFile()) {
            throw new RuntimeException("Could not find 'jcmd' executable for Java home directory " + javaHome + ". Make sure your JAVA_HOME variable points to a JDK.");
        }
        this.jcmd = jcmd;
    }

    private String jcmdPath() {
        return "bin/jcmd" + (OperatingSystem.isWindows() ? ".exe" : "");
    }

    public void run(String pid, String... command) {
        List<String> commandLine = new ArrayList<>();
        commandLine.add(jcmd.getAbsolutePath());
        commandLine.add(pid);
        commandLine.addAll(Arrays.asList(command));
        new CommandExec().run(commandLine);
    }

}
