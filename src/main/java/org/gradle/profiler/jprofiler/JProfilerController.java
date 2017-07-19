package org.gradle.profiler.jprofiler;

import org.gradle.profiler.Invoker;
import org.gradle.profiler.Logging;
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
import java.util.logging.Logger;

public class JProfilerController implements ProfilerController {

    public static final int CONNECT_TIMEOUT = 5000;

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
    public void start() throws IOException, InterruptedException {
        if (profileWholeLifeTime()) {
            startOnceGradleStarts();
        } else {
            startNow();
        }
    }

    private void startOnceGradleStarts() {
        Thread thread = new Thread(this::tryStartNow);
        thread.setName("JProfiler connector");
        thread.start();
    }

    private void tryStartNow() {
        long start = System.currentTimeMillis();
        Exception lastProblem = null;
        boolean started = false;

        while (!started && System.currentTimeMillis() - start < CONNECT_TIMEOUT) {
            try {
                startNow();
                started = true;
            } catch (Exception e) {
                lastProblem = e;
                try {
                    Thread.sleep(10);
                } catch (InterruptedException interrupt) {
                    throw new RuntimeException(interrupt);
                }
            }
        }
        if (!started) {
            throw new IllegalStateException("Failed to connect to build VM after " + CONNECT_TIMEOUT + "ms", lastProblem);
        }
    }

    private void startNow() throws IOException {
        invoke("startCPURecording", true);
        if (jProfilerConfig.isRecordAlloc()) {
            invoke("startAllocRecording", true);
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
        if (profileWholeLifeTime()) {
            invoke("saveSnapshotOnExit", getSnapshotPath());
        }
    }

    @Override
    public void stop() throws IOException, InterruptedException {
        if (!profileWholeLifeTime()) {
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
            if (jProfilerConfig.isHeapDump()) {
                invoke("triggerHeapDump");
            }
            invoke("saveSnapshot", getSnapshotPath());
        }
        closeConnection();
    }

    private String getSnapshotPath() {
        File outputDir = settings.getScenario().getOutputDir();
        String snapshotName = settings.getScenario().getProfileName();

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
            JMXServiceURL jmxUrl = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://localhost:" + jProfilerConfig.getPort() + "/jmxrmi");
            JMXConnector newConnector = JMXConnectorFactory.newJMXConnector(jmxUrl, Collections.emptyMap());
            newConnector.connect();
            connection = newConnector.getMBeanServerConnection();
            objectName = new ObjectName("com.jprofiler.api.agent.mbean:type=RemoteController");
            if (!connection.isRegistered(objectName)) {
                throw new RuntimeException("JProfiler MBean not found");
            }
            connector = newConnector;
        }
    }

    private void closeConnection() {
        try {
            connector.close();
        } catch (IOException e) {
            Logging.detailed().println("Could not close connection to profiled VM. This is normal when running in no-daemon mode");
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

    private boolean hasOperation(String operationName) throws IOException {
        try {
            ensureConnected();
            return Arrays.stream(connection.getMBeanInfo(objectName).getOperations())
                    .anyMatch(operationInfo -> operationInfo.getName().equals(operationName));
        } catch (InstanceNotFoundException | ReflectionException | MalformedObjectNameException | IntrospectionException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean profileWholeLifeTime() {
        return settings.getInvocationSettings().getInvoker().equals(Invoker.NoDaemon);
    }
}
