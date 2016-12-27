package org.gradle.profiler.jprofiler;

import org.gradle.profiler.JvmArgsCalculator;
import org.gradle.profiler.OperatingSystem;
import org.gradle.profiler.ScenarioSettings;

import java.io.File;
import java.io.IOException;
import java.net.*;
import java.nio.file.Paths;
import java.util.List;

public class JProfilerJvmArgsCalculator extends JvmArgsCalculator {

    private final ScenarioSettings settings;

    public JProfilerJvmArgsCalculator(ScenarioSettings settings) {
        this.settings = settings;
    }

    @Override
    public void calculateJvmArgs(List<String> jvmArgs) {
        int port = findUnusedPort();
        getJProfilerConfig().setPort(port);
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
        String sessionId = getJProfilerConfig().getSessionId();
        if (sessionId != null) {
            builder.append("id=").append(sessionId);
        } else {
            builder.append("id=1,config=").append(getConfigFile());
        }
        builder.append(",sysprop=jprofiler.jmxServerPort=").append(port);
        return builder.toString();
    }

    private File getJProfilerDir() {
        JProfilerConfig profilerOptions = getJProfilerConfig();
        String homeDir = profilerOptions.getHomeDir();
        if (OperatingSystem.isMacOS() && !new File(homeDir, "bin").exists()) {
            return new File(homeDir, "Contents/Resources/app");
        } else {
            return new File(homeDir);
        }
    }

    private JProfilerConfig getJProfilerConfig() {
        return (JProfilerConfig)settings.getInvocationSettings().getProfilerOptions();
    }

    private File getConfigFile() {
        try {
            String config = getJProfilerConfig().getConfig();
            String resourceName = "/jprofiler/config/" + config + ".xml";
            URL resource = getClass().getResource(resourceName);
            if (resource == null) {
                throw new RuntimeException("Classpath resource \"" + resourceName + "\" not found");
            } else {
                return Paths.get(resource.toURI()).toFile();
            }
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
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
