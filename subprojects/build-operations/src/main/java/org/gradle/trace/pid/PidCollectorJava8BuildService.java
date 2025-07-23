package org.gradle.trace.pid;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.lang.reflect.InvocationTargetException;

public abstract class PidCollectorJava8BuildService extends AbstractPidCollectorBuildService {

    @Override
    protected Long getPid() {
        return ProcessHandle.current().pid();
    }
}
