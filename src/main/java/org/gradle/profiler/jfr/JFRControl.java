package org.gradle.profiler.jfr;

import org.gradle.profiler.CommandExec;
import org.gradle.profiler.OperatingSystem;
import org.gradle.profiler.ProfilerController;
import org.gradle.profiler.ScenarioSettings;
import org.gradle.profiler.fg.FlameGraphGenerator;
import org.gradle.profiler.fg.FlameGraphSanitizer;

import java.io.File;
import java.io.IOException;

public class JFRControl implements ProfilerController {
    private static final String PROFILE_JFR_SUFFIX = ".jfr";
    private static final String PROFILE_TXT_SUFFIX = "-jfr.txt";
    private static final String PROFILE_SANITIZED_TXT_SUFFIX = "-jfr-sanitized.txt";
    private static final String FLAMES_SVG_SUFFX = "-jfr-flames.svg";

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
            throw new RuntimeException("Could not find 'jcmd' executable for Java home directory " + javaHome);
        }
        this.jcmd = jcmd;
        this.jfrArgs = args;
        this.pid = pid;

    }

    private String jcmdPath() {
        return "bin/jcmd" + (OperatingSystem.isWindows() ? ".exe" : "");
    }

    @Override
    public void start() throws IOException, InterruptedException {
        run(jcmd.getAbsolutePath(), pid, "JFR.start", "name=profile", "settings=profile", "duration=0");
    }

    @Override
    public void stop() throws IOException, InterruptedException {
        File jfrFile = new File(getOutputDir(), getProfileName() + PROFILE_JFR_SUFFIX);
        run(jcmd.getAbsolutePath(), pid, "JFR.stop", "name=profile", "filename=" + jfrFile.getAbsolutePath());
        if(canProduceFlameGraphs()) {
            File txtFile = new File( getOutputDir(), getProfileName() + PROFILE_TXT_SUFFIX);
            File sanitizedTxtFile = new File( getOutputDir(), getProfileName() + PROFILE_SANITIZED_TXT_SUFFIX);
            File fgFile = new File( getOutputDir(), getProfileName() + FLAMES_SVG_SUFFX);
            convertToFlameGraphTxtFile( jfrFile, txtFile );
            sanitizeFlameGraphTxtFile( txtFile, sanitizedTxtFile );
            generateFlameGraph( sanitizedTxtFile, fgFile );
        }
        System.out.println("Wrote profiling data to " + jfrFile.getPath());
    }

    private boolean canProduceFlameGraphs() {
        return findJfrFgJar() != null && findFlamegraphPl().exists();
    }

    private void convertToFlameGraphTxtFile(File jfrFile, File txtFile) throws IOException, InterruptedException
    {
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome == null) {
            throw new IllegalArgumentException("Please set the JAVA_HOME environment variable to your Java installation");
        }
        String java = javaHome + File.separatorChar + "bin" + File.separatorChar + "java";
        run(java, "-jar", findJfrFgJar().getAbsolutePath(), "folded",
             "-f", jfrFile.getAbsolutePath(),
             "-o", txtFile.getAbsolutePath());
    }

    private void sanitizeFlameGraphTxtFile(final File txtFile, final File sanitizedTxtFile) throws IOException {
        new FlameGraphSanitizer( FlameGraphSanitizer.DEFAULT_SANITIZE_FUNCTION ).sanitize( txtFile, sanitizedTxtFile );
    }

    private void generateFlameGraph(final File sanitizedTxtFile, final File fgFile) throws IOException, InterruptedException {
        new FlameGraphGenerator( jfrArgs.getFgHomeDir() ).generateFlameGraph( sanitizedTxtFile, fgFile );
    }

    private File findJfrFgJar() {
        File target = new File( jfrArgs.getJfrFgHomeDir(), "target" );
        if(target.exists()) {
            File[] found = target.listFiles(( dir, name ) -> name.startsWith( "flamegraph-output-" ) && name.endsWith( ".jar" ) );
            if(found != null && found.length > 0) {
                return found[ 0 ];
            }
        }
        return null;
    }

    private File findFlamegraphPl() {
        return new File(jfrArgs.getFgHomeDir(), "flamegraph.pl");
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
