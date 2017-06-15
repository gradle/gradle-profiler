package org.gradle.profiler.jprofiler;

import org.gradle.profiler.JvmArgsCalculator;
import org.gradle.profiler.OperatingSystem;
import org.gradle.profiler.ScenarioSettings;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public class JProfilerJvmArgsCalculator extends JvmArgsCalculator {
    private final JProfilerConfig jProfilerConfig;
    private ScenarioSettings settings;

    public JProfilerJvmArgsCalculator(JProfilerConfig jProfilerConfig, ScenarioSettings settings) {
        this.jProfilerConfig = jProfilerConfig;
        this.settings = settings;
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
                id = sessionId;
            } else {
                configFile = null;
                id = null;
            }
        } else {
            configFile = getConfigFile();
            id = "1";
        }
        if (configFile != null) {
            builder.append("id=").append(id).append(",config=").append(transformConfigFile(configFile, id));
        }

        builder.append(",sysprop=jprofiler.jmxServerPort=").append(port);
        return builder.toString();
    }

    private File transformConfigFile(File configFile, String id) {
        if (profileWholeLifeTime()) {
            try {
                File transformedConfigFile = deleteOnExit(File.createTempFile("jprofiler", ".xml"));
                File probesFile = createProbesDocument();
                URL resource = getClass().getResource("/jprofiler/transform.xsl");
                Templates template = TransformerFactory.newInstance().newTemplates(new StreamSource(resource.openStream()));
                Source source = new StreamSource(new FileInputStream(configFile));
                Result result = new StreamResult(new FileOutputStream(transformedConfigFile));
                Transformer transformer = template.newTransformer();
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.setParameter("id", id);
                transformer.setParameter("allocRecording", jProfilerConfig.isRecordAlloc());
                transformer.setParameter("monitorRecording", jProfilerConfig.isRecordMonitors());
                transformer.setParameter("probesFile", probesFile.getPath());
                transformer.setParameter("snapshotPath", getSnapshotPath());
                transformer.transform(source, result);
                if (Boolean.getBoolean("jprofiler.debugTransform")) {
                    Files.readAllLines(transformedConfigFile.toPath()).forEach(System.out::println);
                }
                return transformedConfigFile;
            } catch (TransformerException | IOException | ParserConfigurationException e) {
                throw new RuntimeException(e);
            }
        } else {
            return configFile;
        }
    }

    private File createProbesDocument() throws ParserConfigurationException, TransformerException, IOException {
        File probesFile = deleteOnExit(File.createTempFile("jprofiler", ".xml"));
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element probesElement = document.createElement("probes");
        for (String probeName : jProfilerConfig.getRecordedProbes()) {
            Element probeElement = document.createElement("probe");
            probeElement.setAttribute("name", probeName);
            probeElement.setAttribute("events", String.valueOf(jProfilerConfig.getProbesWithEventRecording().contains(probeName)));
            probeElement.setAttribute("recordSpecial", String.valueOf(jProfilerConfig.getProbesWithSpecialRecording().contains(probeName)));
            probesElement.appendChild(probeElement);
        }
        document.appendChild(probesElement);
        DOMSource source = new DOMSource(document);
        StreamResult result = new StreamResult(new FileOutputStream(probesFile));
        TransformerFactory.newInstance().newTransformer().transform(source, result);
        return probesFile;
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
                Path tmpFile = Files.createTempFile("jprofiler", ".xml");
                try (InputStream inputStream = resource.openStream()) {
                    Files.copy(inputStream, tmpFile, StandardCopyOption.REPLACE_EXISTING);
                }
                return deleteOnExit(tmpFile.toFile());
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not create JProfiler config file.", e);
        }
    }

    private File deleteOnExit(File tmpFile) {
        tmpFile.deleteOnExit();
        return tmpFile;
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

    private boolean profileWholeLifeTime() {
        return JProfiler.profileWholeLifeTime(settings);
    }

    private String getSnapshotPath() {
        return JProfiler.getSnapshotPath(settings);
    }

}
