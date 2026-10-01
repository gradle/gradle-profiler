package org.gradle.profiler.perfetto.jfr.fixture;

import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

@Name("jdk.JVMInformation")
@Label("Synthetic JVM Information")
@Category("JVM")
public class SyntheticJvmInformationEvent extends Event {
    public long pid;
}
