package org.gradle.profiler.jfr;

import org.gradle.profiler.CommandExec;
import org.gradle.profiler.OperatingSystem;
import org.gradle.profiler.ScenarioSettings;
import org.gradle.profiler.SingleRecordingProfilerController;

import java.io.File;

public class JFRControl extends SingleRecordingProfilerController {
    private static final String PROFILE_JFR_SUFFIX = ".jfr";

    private final File jcmd;
    private final JFRArgs jfrArgs;
    private final String pid;
    private final ScenarioSettings scenarioSettings;
    public JFRControl( final JFRArgs args, final String pid, final ScenarioSettings scenarioSettings) {
        this.scenarioSettings = scenarioSettings;
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
        this.pid = pid;

    }

    private String jcmdPath() {
        return "bin/jcmd" + (OperatingSystem.isWindows() ? ".exe" : "");
    }

    @Override
    public void doStartRecording() {
        run(jcmd.getAbsolutePath(), pid, "JFR.start", "name=profile", "settings=" + jfrArgs.getJfrSettings());
    }

    @Override
    public void stopSession() {
        File jfrFile = new File(getOutputDir(), getProfileName() + PROFILE_JFR_SUFFIX);
        run(jcmd.getAbsolutePath(), pid, "JFR.stop", "name=profile", "filename=" + jfrFile.getAbsolutePath());
        new JfrFlameGraphGenerator().generateGraphs(jfrFile);
        System.out.println("Wrote profiling data to " + jfrFile.getPath());
    }

    @Override
    public String getName() {
        return "jfr";
    }

    private void run(String... commandLine) {
        new CommandExec().run(commandLine);
    }

    private File getOutputDir() {
        return scenarioSettings.getScenario().getOutputDir();
    }

    private String getProfileName() {
        return scenarioSettings.getScenario().getProfileName();
    }
}
