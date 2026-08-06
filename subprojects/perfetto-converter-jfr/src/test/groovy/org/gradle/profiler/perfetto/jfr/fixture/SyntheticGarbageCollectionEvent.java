package org.gradle.profiler.perfetto.jfr.fixture;

import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

@Name("jdk.GarbageCollection")
@Label("Synthetic Garbage Collection")
@Category({"JVM", "GC"})
public class SyntheticGarbageCollectionEvent extends Event {
    public String name;
    public String cause;
}
