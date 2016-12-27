package org.gradle.profiler.jprofiler;

import org.gradle.profiler.ProfilerController;
import org.gradle.profiler.ScenarioSettings;

import javax.management.*;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

public class JProfilerController implements ProfilerController {

    private MBeanServerConnection connection;
    private JMXConnector connector;
    private ObjectName objectName;
    private ScenarioSettings settings;

    public JProfilerController(ScenarioSettings settings) {
        this.settings = settings;
    }

    @Override
    public void start() throws IOException, InterruptedException {
        JProfilerConfig config = getJProfilerConfig();
        invoke("startCPURecording", true);
        if (config.isRecordAlloc()) {
            invoke("startAllocRecording", true);
        }
        if (config.isRecordMonitors()) {
            invoke("startMonitorRecording");
        }
        for (String probeName : config.getRecordedProbes()) {
            boolean eventRecording = config.getProbesWithEventRecording().contains(probeName);
            boolean specialRecording = config.getProbesWithSpecialRecording().contains(probeName);
            invoke("startProbeRecording", probeName, eventRecording, specialRecording);
        }
    }

    @Override
    public void stop() throws IOException, InterruptedException {
        JProfilerConfig config = getJProfilerConfig();
        invoke("stopCPURecording");
        if (config.isRecordAlloc()) {
            invoke("stopAllocRecording");
        }
        if (config.isRecordMonitors()) {
            invoke("stopMonitorRecording");
        }
        for (String probeName : config.getRecordedProbes()) {
            invoke("stopProbeRecording", probeName);
        }
        if (config.isHeapDump()) {
            invoke("triggerHeapDump");
        }
        invoke("saveSnapshot", getSnapshotPath());
        closeConnection();
    }

    private String getSnapshotPath() {
        File outputDir = settings.getScenarioOutputDir();
        String snapshotName = settings.getScenario().getName();

        int i = 0;
        File snapshotFile;
        do {
            snapshotFile = new File(outputDir, snapshotName  + ( i == 0 ? "" : ("_" + i)) + ".jps");
            ++i;
        } while (snapshotFile.exists());
        return snapshotFile.getAbsolutePath();
    }

    private void ensureConnected() throws IOException, MalformedObjectNameException {
        if (connector == null) {
            JMXServiceURL jmxUrl = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://localhost:" + getJProfilerConfig().getPort() + "/jmxrmi");
            connector = JMXConnectorFactory.newJMXConnector(jmxUrl, Collections.emptyMap());
            connector.connect();
            connection = connector.getMBeanServerConnection();
            objectName = new ObjectName("com.jprofiler.api.agent.mbean:type=RemoteController");
            if (!connection.isRegistered(objectName)) {
                throw new RuntimeException("JProfiler MBean not found");
            }
        }
    }

    private void closeConnection() throws IOException {
        try {
            connector.close();
        } finally {
            connector = null;
        }
    }

    private void invoke(String operationName, Object... parameterValues) throws IOException {
        String[] parameterTypes = Arrays.stream(parameterValues)
                .map(Object::getClass)
                .map(this::replaceWrapperWithPrimitive)
                .map(Class::getName)
                .toArray(String[]::new);
        try {
            ensureConnected();
            connection.invoke(objectName, operationName, parameterValues, parameterTypes);
        } catch (InstanceNotFoundException | MBeanException | ReflectionException | MalformedObjectNameException e) {
            throw new RuntimeException(e);
        }
    }

    private Class replaceWrapperWithPrimitive(Class c) {
        if (c == Boolean.class) {
            return Boolean.TYPE;
        } else if (c == Integer.class) {
            return Integer.TYPE;
        } else {
            return c;
        }
    }

    private JProfilerConfig getJProfilerConfig() {
        return (JProfilerConfig)settings.getInvocationSettings().getProfilerOptions();
    }
}
