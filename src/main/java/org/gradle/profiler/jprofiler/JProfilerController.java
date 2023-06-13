package org.gradle.profiler.jprofiler;

import org.gradle.profiler.InstrumentingProfiler;
import org.gradle.profiler.Logging;
import org.gradle.profiler.ScenarioSettings;

import javax.management.*;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

public class JProfilerController implements InstrumentingProfiler.SnapshotCapturingProfilerController {

    private final JProfilerConfig jProfilerConfig;
    private final ScenarioSettings settings;

    private MBeanServerConnection connection;
    private JMXConnector connector;
    private ObjectName objectName;

    public JProfilerController(ScenarioSettings settings, JProfilerConfig jProfilerConfig) {
        this.settings = settings;
        this.jProfilerConfig = jProfilerConfig;
    }

    @Override
    public void startRecording(String pid) throws IOException, InterruptedException {
        invoke("startCPURecording", false);
        if (jProfilerConfig.isRecordAlloc()) {
            invoke("startAllocRecording", false);
        }
        if (jProfilerConfig.isRecordMonitors()) {
            invoke("startMonitorRecording");
        }
        for (String probeName : jProfilerConfig.getRecordedProbes()) {
            boolean eventRecording = jProfilerConfig.getProbesWithEventRecording().contains(probeName);
            boolean specialRecording = jProfilerConfig.getProbesWithSpecialRecording().contains(probeName);
            invoke("startProbeRecording", probeName, eventRecording, specialRecording);
        }
        if (jProfilerConfig.isHeapDump() && hasOperation("markHeap")) { // available in JProfiler 10
            invoke("markHeap");
        }
    }

    @Override
    public void stopRecording(String pid) throws IOException, InterruptedException {
        invoke("stopCPURecording");
        if (jProfilerConfig.isRecordAlloc()) {
            invoke("stopAllocRecording");
        }
        if (jProfilerConfig.isRecordMonitors()) {
            invoke("stopMonitorRecording");
        }
        for (String probeName : jProfilerConfig.getRecordedProbes()) {
            invoke("stopProbeRecording", probeName);
        }
    }

    @Override
    public void captureSnapshot(String pid) throws IOException, InterruptedException {
        if (jProfilerConfig.isHeapDump()) {
            invoke("triggerHeapDump");
        }
        invoke("saveSnapshot", getSnapshotPath());
    }

    @Override
    public void stopSession() throws IOException, InterruptedException {
        closeConnection();
    }

    private String getSnapshotPath() {
        return JProfiler.getSnapshotPath(settings);
    }

    private void ensureConnected() throws IOException {
        if (connector == null) {
            try {
                JMXServiceURL jmxUrl = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://localhost:" + jProfilerConfig.getPort() + "/jmxrmi");
                JMXConnector newConnector = JMXConnectorFactory.newJMXConnector(jmxUrl, Collections.emptyMap());
                newConnector.connect();
                connection = newConnector.getMBeanServerConnection();
                objectName = new ObjectName("com.jprofiler.api.agent.mbean:type=RemoteController");
                if (!connection.isRegistered(objectName)) {
                    throw new RuntimeException("JProfiler MBean not found");
                }
                connector = newConnector;
            } catch (MalformedObjectNameException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void closeConnection() {
        if (connector != null) {
            try {
                connector.close();
            } catch (IOException e) {
                Logging.detailed().println("Could not close connection to profiled VM.");
            } finally {
                connector = null;
            }
        }
    }

    private void invoke(String operationName, Object... parameterValues) throws IOException {
        ensureConnected();
        String[] parameterTypes = Arrays.stream(parameterValues)
                .map(Object::getClass)
                .map(this::replaceWrapperWithPrimitive)
                .map(Class::getName)
                .toArray(String[]::new);
        try {
            connection.invoke(objectName, operationName, parameterValues, parameterTypes);
        } catch (InstanceNotFoundException | MBeanException | ReflectionException e) {
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

    private boolean hasOperation(String operationName) throws IOException {
        try {
            return Arrays.stream(connection.getMBeanInfo(objectName).getOperations())
                    .anyMatch(operationInfo -> operationInfo.getName().equals(operationName));
        } catch (InstanceNotFoundException | ReflectionException | IntrospectionException e) {
            throw new RuntimeException(e);
        }
    }
}
