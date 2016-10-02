package net.rubygrapefruit.gradle.profiler;

import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.build.BuildEnvironment;

import java.io.*;

class GradleVersionInspector {
    private final File projectDir;
    private final File initScript;
    private final File gradleHomeFile;

    public GradleVersionInspector(File projectDir) throws IOException {
        this.projectDir = projectDir;
        initScript = File.createTempFile("gradle-profiler", ".gradle");
        initScript.deleteOnExit();
        gradleHomeFile = File.createTempFile("gradle-profiler", "gradle-home");
        gradleHomeFile.deleteOnExit();
        generateInitScript();
    }

    private void generateInitScript() throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(initScript))) {
            writer.println("new File(new URI('" + gradleHomeFile.toURI() + "')).text = gradle.gradleHomeDir");
        }
    }

    public GradleVersion resolve(String versionString) throws IOException {
        File dir = new File(versionString);
        if (dir.isDirectory()) {
            dir = dir.getCanonicalFile();
            return probe(GradleConnector.newConnector().useInstallation(dir));
        }
        if (versionString.matches("\\d+(\\.\\d)+(-\\w+)*")) {
            return probe(GradleConnector.newConnector().useGradleVersion(versionString));
        }
        throw new IllegalArgumentException("Unrecognized Gradle version '" + versionString + "' specified.");
    }

    public GradleVersion defaultVersion() throws IOException {
        return probe(GradleConnector.newConnector());
    }

    private File getGradleHomeForLastBuild() throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(gradleHomeFile))) {
            return new File(reader.readLine());
        }
    }

    private GradleVersion probe(GradleConnector connector) throws IOException {
        ProjectConnection connection = connector.forProjectDirectory(projectDir).connect();
        try {
            BuildEnvironment buildEnvironment = connection.getModel(BuildEnvironment.class);
            BuildInvoker.run(connection.newBuild(), build -> {
                build.withArguments("-I", initScript.getAbsolutePath());
                build.forTasks("help");
                build.run();
                return null;
            });
            return new GradleVersion(buildEnvironment.getGradle().getGradleVersion(), getGradleHomeForLastBuild());
        } finally {
            connection.close();
        }
    }
}
