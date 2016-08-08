package net.rubygrapefruit.gradle.profiler;

import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.build.BuildEnvironment;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {
        OptionParser parser = new OptionParser();
        ArgumentAcceptingOptionSpec<String> projectOption = parser.accepts("project-dir").withRequiredArg();
        OptionSet parsedOptions = parser.parse(args);
        File projectDir = (parsedOptions.has(projectOption) ? new File(parsedOptions.valueOf(projectOption)) : new File(".")).getCanonicalFile();
        System.out.println();
        System.out.println("Project dir: " + projectDir);
        List<?> tasks = parsedOptions.nonOptionArguments();
        System.out.println("Tasks: " + tasks);

        PidInstrumentation pidInstrumentation = new PidInstrumentation();
        JFRControl jfrControl = new JFRControl();

        GradleConnector connector = GradleConnector.newConnector();
        ProjectConnection projectConnection = connector.forProjectDirectory(projectDir).connect();
        try {
            BuildEnvironment buildEnvironment = projectConnection.getModel(BuildEnvironment.class);
            System.out.println("Gradle version: " + buildEnvironment.getGradle().getGradleVersion());
            System.out.println("Java home: " + buildEnvironment.getJava().getJavaHome());
            System.out.println("OS name: " + System.getProperty("os.name"));
            List<String> jvmArgs = new ArrayList<>(buildEnvironment.getJava().getJvmArguments());
            jvmArgs.add("-XX:+UnlockCommercialFeatures");
            jvmArgs.add("-XX:+FlightRecorder");
            System.out.println("Java args: " + jvmArgs);

            startOperation("Running warm-up build #1 with tasks " + tasks);
            runBuild(projectConnection, tasks, jvmArgs, pidInstrumentation);
            String pid = pidInstrumentation.getPidForLastBuild();

            startOperation("Running warm-up build #2 with tasks " + tasks);
            runBuild(projectConnection, tasks, jvmArgs, pidInstrumentation);
            checkPid(pid, pidInstrumentation.getPidForLastBuild());

            startOperation("Starting recording for daemon with pid " + pid);
            jfrControl.start(pid);

            startOperation("Running profiling build with tasks " + tasks);
            runBuild(projectConnection, tasks, jvmArgs, pidInstrumentation);
            checkPid(pid, pidInstrumentation.getPidForLastBuild());

            startOperation("Stopping recording for daemon with pid " + pid);
            jfrControl.stop(pid, new File("profile.jfr"));
        } finally {
            projectConnection.close();
        }
        System.out.println();
    }

    private static void startOperation(String name) {
        System.out.println();
        System.out.println("* " + name);
    }

    private static void checkPid(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new RuntimeException("Multiple Gradle daemons were used. Please make sure all Gradle daemons are stopped before running the profiler.");
        }
    }

    private static void runBuild(ProjectConnection projectConnection, List<?> tasks, List<String> jvmArgs, PidInstrumentation pidInstrumentation) throws IOException {
        BuildLauncher build = projectConnection.newBuild();
        build.forTasks(tasks.toArray(new String[0]));
        build.withArguments(pidInstrumentation.getArgs());
        build.setJvmArguments(jvmArgs);
        build.run();
        System.out.println("Used daemon with pid " + pidInstrumentation.getPidForLastBuild());
    }

    static class JFRControl {
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

    static class PidInstrumentation {
        private final File initScript;
        private final File pidFile;

        PidInstrumentation() throws IOException {
            initScript = File.createTempFile("gradle-profiler", ".gradle");
            pidFile = File.createTempFile("gradle-profiler", "pid");
            generateInitScript();
        }

        private void generateInitScript() throws IOException {
            try (PrintWriter writer = new PrintWriter(new FileWriter(initScript))) {
                writer.println("def e = services.get(org.gradle.internal.nativeintegration.ProcessEnvironment)");
                writer.println("new File(new URI('" + pidFile.toURI() + "')).text = e.pid");
            }
        }

        public List<String> getArgs() {
            return Arrays.asList("-I", initScript.getAbsolutePath());
        }

        public String getPidForLastBuild() throws IOException {
            try (BufferedReader reader = new BufferedReader(new FileReader(pidFile))) {
                return reader.readLine();
            }
        }
    }
}
