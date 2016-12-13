package net.rubygrapefruit.gradle.profiler;

import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.build.BuildEnvironment;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

class GradleVersionInspector {
    private final File projectDir;
    private final DaemonControl daemonControl;
    private final File initScript;
    private final File gradleHomeFile;
    private final Map<String, GradleVersion> versions = new HashMap<>();
    private GradleVersion defaultVersion;

    public GradleVersionInspector(File projectDir, DaemonControl daemonControl) throws IOException {
        this.projectDir = projectDir;
        this.daemonControl = daemonControl;
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

    public GradleVersion resolve(String versionString) {
        GradleVersion version = versions.get(versionString);
        if (version == null) {
            version = doResolveVersion(versionString);
            versions.put(versionString, version);
        }
        return version;
    }

    private GradleVersion doResolveVersion(String versionString) {
        Logging.startOperation("Inspecting Gradle version '" + versionString + "'");
        try {
            File dir = new File(versionString);
            if (dir.isDirectory()) {
                dir = dir.getCanonicalFile();
                return probe(GradleConnector.newConnector().useInstallation(dir));
            }
            if (versionString.matches("\\d+(\\.\\d+)+(-.+)?")) {
                return probe(GradleConnector.newConnector().useGradleVersion(versionString));
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not locate Gradle distribution for requested version '" + versionString + "'.", e);
        }
        throw new IllegalArgumentException("Unrecognized Gradle version '" + versionString + "' specified.");
    }

    public GradleVersion defaultVersion() {
        if (defaultVersion == null) {
            Logging.startOperation("Locating default Gradle version");
            defaultVersion = probe(GradleConnector.newConnector());
        }
        return defaultVersion;
    }

    private File getGradleHomeForLastBuild() {
        try {
            try (BufferedReader reader = new BufferedReader(new FileReader(gradleHomeFile))) {
                return new File(reader.readLine());
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not locate Gradle home directory.", e);
        }
    }

    private GradleVersion probe(GradleConnector connector) {
        GradleVersion version;
        ProjectConnection connection = connector.forProjectDirectory(projectDir).connect();
        try {
            BuildEnvironment buildEnvironment = connection.getModel(BuildEnvironment.class);
            BuildInvoker.run(connection.newBuild(), build -> {
                build.withArguments("-I", initScript.getAbsolutePath());
                build.forTasks("help");
                build.run();
                return null;
            });
            version = new GradleVersion(buildEnvironment.getGradle().getGradleVersion(), getGradleHomeForLastBuild());
        } finally {
            connection.close();
        }
        daemonControl.stop(version);
        return version;
    }
}
