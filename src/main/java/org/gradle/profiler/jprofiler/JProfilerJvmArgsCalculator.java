package org.gradle.profiler.jprofiler;

import org.gradle.profiler.JvmArgsCalculator;
import org.gradle.profiler.OperatingSystem;
import org.gradle.profiler.ScenarioSettings;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public class JProfilerJvmArgsCalculator implements JvmArgsCalculator {
    private final JProfilerConfig jProfilerConfig;
    private final ScenarioSettings settings;
    private final boolean startRecordingOnProcessStart;
    private final boolean captureSnapshotOnProcessExit;

    public JProfilerJvmArgsCalculator(JProfilerConfig jProfilerConfig, ScenarioSettings settings, boolean startRecordingOnProcessStart, boolean captureSnapshotOnProcessExit) {
        this.jProfilerConfig = jProfilerConfig;
        this.settings = settings;
        this.startRecordingOnProcessStart = startRecordingOnProcessStart;
        this.captureSnapshotOnProcessExit = captureSnapshotOnProcessExit;
    }

    @Override
    public void calculateJvmArgs(List<String> jvmArgs) {
        int port = findUnusedPort();
        jProfilerConfig.setPort(port);
        jvmArgs.add(getAgentPathParameter(port));
    }

    private String getAgentPathParameter(int port) {
        File jprofilerDir = getJProfilerDir();
        if (!jprofilerDir.exists()) {
            throw new RuntimeException("JProfiler home directory " + jprofilerDir + " does not exist, please specify a different directory with --jprofiler-home");
        }
        boolean is64Bit = System.getProperty("sun.arch.data.model", "64").equals("64");

        StringBuilder builder = new StringBuilder();
        builder.append("-agentpath:").append(jprofilerDir);
        if (!jprofilerDir.getPath().endsWith(File.separator)) {
            builder.append(File.separator);
        }
        builder.append("bin").append(File.separator);
        if (OperatingSystem.isWindows()) {
            builder.append("windows");
            if (is64Bit) {
                builder.append("-x64");
            }
            builder.append(File.separator);
            builder.append("jprofilerti.dll");
        } else if (OperatingSystem.isMacOS()) {
            builder.append("macos").append(File.separator);
            builder.append("libjprofilerti.jnilib");
        } else if (OperatingSystem.isLinuxX86()) {
            builder.append("linux");
            if (is64Bit) {
                builder.append("-x64");
            } else {
                builder.append("-x86");
            }
            builder.append(File.separator);
            builder.append("libjprofilerti.so");
        } else {
            throw new RuntimeException("Currently only Windows, macOS and Linux-x86 are detected for the native JProfiler agent library");
        }

        builder.append("=offline,");
        String sessionId = jProfilerConfig.getSessionId();
        File configFile;
        String id;
        if (sessionId != null) {
            if (jProfilerConfig.getConfigFile() != null) {
                configFile = new File(jProfilerConfig.getConfigFile());
            } else {
                configFile = null;
            }
            id = sessionId;
        } else {
            configFile = getConfigFile();
            id = "1";
        }
        builder.append("id=").append(id);
        if (configFile != null) {
            builder.append(",config=").append(transformConfigFile(configFile, id));
        }

        builder.append(",sysprop=jprofiler.jmxServerPort=").append(port);
        return builder.toString();
    }

    private File transformConfigFile(File configFile, String id) {
        if (startRecordingOnProcessStart) {
            return JProfilerConfigFileTransformer.transform(configFile, id, jProfilerConfig, JProfiler.getSnapshotPath(settings), captureSnapshotOnProcessExit);
        } else {
            return configFile;
        }
    }

    private File getJProfilerDir() {
        String homeDir = jProfilerConfig.getHomeDir();
        if (OperatingSystem.isMacOS() && !new File(homeDir, "bin").exists()) {
            return new File(homeDir, "Contents/Resources/app");
        } else {
            return new File(homeDir);
        }
    }

    private File getConfigFile() {
        try {
            String resourceName = "/jprofiler/config/" + jProfilerConfig.getConfig() + ".xml";
            URL resource = getClass().getResource(resourceName);
            if (resource == null) {
                throw new RuntimeException("Classpath resource \"" + resourceName + "\" not found");
            } else {
                Path tmpPath = Files.createTempFile("jprofiler", ".xml");
                try (InputStream inputStream = resource.openStream()) {
                    Files.copy(inputStream, tmpPath, StandardCopyOption.REPLACE_EXISTING);
                }
                File tmpFile = tmpPath.toFile();
                tmpFile.deleteOnExit();
                return tmpFile;
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not create JProfiler config file.", e);
        }
    }

    private static int findUnusedPort() {
        ServerSocket ss = null;
        try {
            if (OperatingSystem.isWindows() || OperatingSystem.isMacOS()) {
                ss = new ServerSocket();
                SocketAddress sa = new InetSocketAddress("127.0.0.1", 0);
                ss.bind(sa);
                ss.setReuseAddress(true);
                return ss.getLocalPort();
            } else {
                ss = new ServerSocket(0);
                ss.setReuseAddress(true);
                return ss.getLocalPort();
            }
        } catch (IOException e) {
            throw new RuntimeException("Cannot find an unused port", e);
        } finally {
            if (ss != null) {
                try {
                    ss.close();
                } catch (IOException ioe) {
                }
            }
        }
    }
}
