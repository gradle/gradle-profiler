package org.gradle.profiler.jfr;

import java.io.File;
import java.io.IOException;
import org.gradle.profiler.ProfilerController;
import org.gradle.profiler.fg.FlameGraphGenerator;
import org.gradle.profiler.fg.FlameGraphSanitizer;

public class JFRControl implements ProfilerController {
    private static final String PROFILE_JFR = "profile.jfr";
    private static final String PROFILE_TXT = "profile-jfr.txt";
    private static final String PROFILE_SANITIZED_TXT = "profile-jfr-sanitized.txt";
    private static final String FLAMES_SVG = "jfr-flames.svg";

    private final File jcmd;
    private final JFRArgs jfrArgs;
    private final File outputDir;
    private final String pid;

    public JFRControl( final JFRArgs args, final String pid, final File outputDir ) {
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
        this.pid = pid;
        this.outputDir = outputDir;
    }

    @Override
    public void start() throws IOException, InterruptedException {
        run(jcmd.getAbsolutePath(), pid, "JFR.start", "name=profile", "settings=profile", "duration=0");
    }

    @Override
    public void stop() throws IOException, InterruptedException {
        File jfrFile = new File(outputDir, PROFILE_JFR);
        run(jcmd.getAbsolutePath(), pid, "JFR.stop", "name=profile", "filename=" + jfrFile.getAbsolutePath());
        if(canProduceFlameGraphs()) {
            File txtFile = new File( outputDir, PROFILE_TXT );
            File sanitizedTxtFile = new File( outputDir, PROFILE_SANITIZED_TXT );
            File fgFile = new File( outputDir, FLAMES_SVG );
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

    private void run(String... commandLine) throws InterruptedException, IOException {
        ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
        Process process = processBuilder.start();
        int result = process.waitFor();
        if (result != 0) {
            throw new RuntimeException("Command " + commandLine[0] + " failed.");
        }
    }
}
