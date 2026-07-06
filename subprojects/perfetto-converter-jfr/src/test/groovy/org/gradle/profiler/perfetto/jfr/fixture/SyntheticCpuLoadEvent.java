package org.gradle.profiler.perfetto.jfr.fixture;

import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

@Name("jdk.CPULoad")
@Label("Synthetic CPU Load")
@Category({"JVM", "System"})
public class SyntheticCpuLoadEvent extends Event {
    public double jvmUser;
    public double jvmSystem;
    public double machineTotal;
}
