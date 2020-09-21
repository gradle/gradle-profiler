package org.gradle.profiler;

import com.google.common.collect.ImmutableList;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.build.JavaEnvironment;
import org.gradle.util.GUtil;
import org.gradle.util.GradleVersion;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
        initScript = File.createTempFile("gradle-profiler", ".gradle").getCanonicalFile();
        initScript.deleteOnExit();
        buildDetails = File.createTempFile("gradle-profiler", "build-details");
        buildDetails.deleteOnExit();
        generateInitScript();
    }

    private void generateInitScript() throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(initScript))) {
            writer.println(
                "rootProject {\n" +
                    "  afterEvaluate {\n" +
                    "    def detailsFile = new File(new URI('" + buildDetails.toURI() + "'))\n" +
                    "    detailsFile.text = \"${gradle.gradleHomeDir}\\n\"\n" +
                    "    detailsFile << plugins.hasPlugin('com.gradle.build-scan') << '\\n'\n" +
                    "  }\n" +
                    "}\n"
            );
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
            return Files.readAllLines(buildDetails.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Could not read the build's configuration.", e);
        }
    }

    private GradleBuildConfiguration probe(GradleConnector connector) {
        GradleBuildConfiguration version;
        try (ProjectConnection connection = connector.forProjectDirectory(projectDir).connect()) {
            BuildEnvironment buildEnvironment = connection.getModel(BuildEnvironment.class);
            new ToolingApiGradleClient(connection).runTasks(ImmutableList.of("help"), ImmutableList.of("-I", initScript.getAbsolutePath()), ImmutableList.of());
            List<String> buildDetails = readBuildDetails();
            JavaEnvironment javaEnvironment = buildEnvironment.getJava();
            List<String> allJvmArgs = new ArrayList<>(javaEnvironment.getJvmArguments());
            allJvmArgs.addAll(readSystemPropertiesFromGradleProperties());
            version = new GradleBuildConfiguration(
                GradleVersion.version(buildEnvironment.getGradle().getGradleVersion()),
                new File(buildDetails.get(0)),
                javaEnvironment.getJavaHome(),
                allJvmArgs,
                Boolean.valueOf(buildDetails.get(1))
            );
        }
        daemonControl.stop(version);
        return version;
    }

    private List<String> readSystemPropertiesFromGradleProperties() {
        String jvmArgs = getJvmArgsProperty(gradleUserHome);
        if (jvmArgs == null) {
            jvmArgs = getJvmArgsProperty(projectDir);
        }
        if (jvmArgs == null) {
            return Collections.emptyList();
        }
        return ArgumentsSplitter.split(jvmArgs).stream().filter(arg -> arg.startsWith("-D")).collect(Collectors.toList());
    }

    private String getJvmArgsProperty(File scope) {
        File propertyFile = new File(scope, "gradle.properties");
        if (!propertyFile.exists()) {
            return null;
        }
        return GUtil.loadProperties(propertyFile).getProperty("org.gradle.jvmargs");
    }
}
