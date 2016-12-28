package org.gradle.profiler.jprofiler;

import java.util.*;

public class JProfilerConfig {

    private final String homeDir;
    private final String config;
    private final String sessionId;
    private String configFile;
    private final boolean recordAlloc;
    private final boolean recordMonitors;
    private final List<String> recordedProbes;
    private final Set<String> probesWithEventRecording;
    private final Set<String> probesWithSpecialRecording;
    private final boolean heapDump;

    private int port;

    public JProfilerConfig(String homeDir, String config, String sessionId, String configFile, boolean recordAlloc, boolean recordMonitors, boolean heapDump, List<String> recordedProbeSpecs) {
        this.homeDir = homeDir;
        this.sessionId = sessionId;
        this.configFile = configFile;
        this.recordAlloc = recordAlloc;
        this.config = config;
        this.recordMonitors = recordMonitors;
        this.heapDump = heapDump;

        List<String> recordedProbes = new ArrayList<>();
        Set<String> probesWithEventRecording = new HashSet<>();
        Set<String> probesWithSpecialRecording = new HashSet<>();
        for (String probeSpec : recordedProbeSpecs) {
            int separatorPos = probeSpec.indexOf(':');
            if (separatorPos > -1) {
                String probeName = probeSpec.substring(0, separatorPos);
                String probeOptions = probeSpec.substring(separatorPos + 1);
                recordedProbes.add(probeName);
                if (probeOptions.contains("+events")) {
                    probesWithEventRecording.add(probeName);
                }
                if (probeOptions.contains("+special")) {
                    probesWithSpecialRecording.add(probeName);
                }
            } else {
                recordedProbes.add(probeSpec);
            }
        }
        this.recordedProbes = Collections.unmodifiableList(recordedProbes);
        this.probesWithEventRecording = Collections.unmodifiableSet(probesWithEventRecording);
        this.probesWithSpecialRecording = Collections.unmodifiableSet(probesWithSpecialRecording);
    }

    public String getHomeDir() {
        return homeDir;
    }

    public String getConfig() {
        return config;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getConfigFile() {
        return configFile;
    }

    public boolean isRecordAlloc() {
        return recordAlloc;
    }

    public boolean isRecordMonitors() {
        return recordMonitors;
    }

    public List<String> getRecordedProbes() {
        return recordedProbes;
    }

    public Set<String> getProbesWithEventRecording() {
        return probesWithEventRecording;
    }

    public Set<String> getProbesWithSpecialRecording() {
        return probesWithSpecialRecording;
    }

    public boolean isHeapDump() {
        return heapDump;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }
}
