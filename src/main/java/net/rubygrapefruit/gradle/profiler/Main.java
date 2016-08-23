package net.rubygrapefruit.gradle.profiler;

import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.build.BuildEnvironment;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {
        boolean ok;
        try {
            ok = run(args);
        } catch (Exception e) {
            e.printStackTrace(System.out);
            ok = false;
        }
        System.out.println();
        System.out.flush();
        System.exit(ok ? 0 : 1);
    }

    private static boolean run(String[] args) throws Exception {
        OptionParser parser = new OptionParser();
        ArgumentAcceptingOptionSpec<String> projectOption = parser.accepts("project-dir", "the directory to run the build from").withRequiredArg();
        OptionSet parsedOptions;
        try {
            parsedOptions = parser.parse(args);
        } catch (OptionException e) {
            System.out.println(e.getMessage());
            System.out.println();
            parser.printHelpOn(System.out);
            return false;
        }
        if (!parsedOptions.has(projectOption)) {
            System.out.println("No project directory specified.");
            System.out.println();
            parser.printHelpOn(System.out);
            return false;
        }

        setupLogging();

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
            System.out.println("OS name: " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
            List<String> jvmArgs = new ArrayList<>(buildEnvironment.getJava().getJvmArguments());
            jvmArgs.add("-XX:+UnlockCommercialFeatures");
            jvmArgs.add("-XX:+FlightRecorder");
            jvmArgs.add("-XX:FlightRecorderOptions=stackdepth=1024");

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
        return true;
    }

    private static void setupLogging() throws FileNotFoundException {
        File logFile = new File("profile.log");
        OutputStream log = new BufferedOutputStream(new FileOutputStream(logFile));
        System.setOut(new PrintStream(new TeeOutputStream(System.out, log)));
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
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        build.setStandardOutput(outputStream);
        build.setStandardError(outputStream);
        build.setJvmArguments(jvmArgs);
        try {
            build.run();
        } catch (GradleConnectionException e) {
            System.out.println();
            System.out.println("ERROR: failed to run build.");
            System.out.println();
            System.out.append(new String(outputStream.toByteArray()));
            System.out.println();
            throw e;
        }
        System.out.println("Used daemon with pid " + pidInstrumentation.getPidForLastBuild());
    }

    static class TeeOutputStream extends OutputStream {
        private final List<OutputStream> targets;

        public TeeOutputStream(OutputStream... targets) {
            this.targets = Arrays.asList(targets);
        }

        @Override
        public void write(int b) throws IOException {
            withAll(stream -> stream.write(b));
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            withAll(stream -> stream.write(b, off, len));
        }

        @Override
        public void flush() throws IOException {
            withAll(stream -> stream.flush());
        }

        @Override
        public void close() throws IOException {
            withAll(stream -> stream.close());
        }

        private void withAll(IOAction action) throws IOException {
            IOException failure = null;
            for (OutputStream target : targets) {
                try {
                    action.withStream(target);
                } catch (IOException e) {
                    failure = e;
                }
            }
            if (failure != null) {
                throw failure;
            }
        }

        private interface IOAction {
            void withStream(OutputStream stream) throws IOException;
        }
    }
}
