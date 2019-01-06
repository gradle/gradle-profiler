package org.gradle.profiler;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.build.JavaEnvironment;
import org.gradle.util.GradleVersion;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultGradleBuildConfigurationReader implements GradleBuildConfigurationReader {
    private final File projectDir;
    private final File gradleUserHome;
    private final DaemonControl daemonControl;
    private final File initScript;
    private final File buildDetails;
    private final Map<String, GradleBuildConfiguration> versions = new HashMap<>();
    private GradleBuildConfiguration defaultVersion;

    public DefaultGradleBuildConfigurationReader(File projectDir, File gradleUserHome, DaemonControl daemonControl) throws IOException {
        this.projectDir = projectDir;
        this.gradleUserHome = gradleUserHome;
        this.daemonControl = daemonControl;
        initScript = File.createTempFile("gradle-profiler", ".gradle");
        initScript.deleteOnExit();
        buildDetails = File.createTempFile("gradle-profiler", "build-details");
        buildDetails.deleteOnExit();
        generateInitScript();
    }

    private void generateInitScript() throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(initScript))) {
            writer.println("def detailsFile = new File(new URI('" + buildDetails.toURI() + "'))");
            writer.println("detailsFile.text = gradle.gradleHomeDir");
            writer.println("rootProject { plugins.withId('com.gradle.build-scan') { detailsFile << '\\nscan' } }");
        }
    }

    @Override
    public GradleBuildConfiguration readConfiguration(String gradleVersion) {
        GradleBuildConfiguration version = versions.get(gradleVersion);
        if (version == null) {
            version = doResolveVersion(gradleVersion);
            versions.put(gradleVersion, version);
        }
        return version;
    }

    private GradleBuildConfiguration doResolveVersion(String versionString) {
        Logging.startOperation("Inspecting the build using Gradle version '" + versionString + "'");
        try {
            File dir = new File(versionString);
            if (dir.isDirectory()) {
                dir = dir.getCanonicalFile();
                return probe(connector().useInstallation(dir));
            }
            if (versionString.matches("\\d+(\\.\\d+)+(-.+)?")) {
                return probe(connector().useGradleVersion(versionString));
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not locate Gradle distribution for requested version '" + versionString + "'.", e);
        }
        throw new IllegalArgumentException("Unrecognized Gradle version '" + versionString + "' specified.");
    }

    @Override
    public GradleBuildConfiguration readConfiguration() {
        if (defaultVersion == null) {
            Logging.startOperation("Inspecting the build using its default Gradle version");
            defaultVersion = probe(connector());
        }
        return defaultVersion;
    }

    private GradleConnector connector() {
        return GradleConnector.newConnector().useGradleUserHomeDir(gradleUserHome.getAbsoluteFile());
    }

    private List<String> readBuildDetails() {
        try {
            return Files.readLines(buildDetails, Charsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Could not read the build's configuration.", e);
        }
    }

    private GradleBuildConfiguration probe(GradleConnector connector) {
        GradleBuildConfiguration version;
        ProjectConnection connection = connector.forProjectDirectory(projectDir).connect();
        try {
            BuildEnvironment buildEnvironment = connection.getModel(BuildEnvironment.class);
            BuildInvoker.run(connection.newBuild(), build -> {
                build.withArguments("-I", initScript.getAbsolutePath());
                build.forTasks("help");
                build.run();
                return null;
            });
            List<String> buildDetails = readBuildDetails();
            JavaEnvironment javaEnvironment = buildEnvironment.getJava();
            version = new GradleBuildConfiguration(
                GradleVersion.version(buildEnvironment.getGradle().getGradleVersion()),
                new File(buildDetails.get(0)),
                javaEnvironment.getJavaHome(),
                javaEnvironment.getJvmArguments(),
                buildDetails.contains("scan")
            );
        } finally {
            connection.close();
        }
        daemonControl.stop(version);
        return version;
    }
}
