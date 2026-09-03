package org.gradle.profiler.perfetto.jfr.fixture;

import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

@Name("jdk.GCHeapSummary")
@Label("Synthetic GC Heap Summary")
@Category({"JVM", "GC"})
public class SyntheticHeapSummaryEvent extends Event {
    public String when;
    public long heapUsed;
    public long heapCommitted;
}
