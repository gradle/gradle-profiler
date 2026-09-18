package org.gradle.profiler.perfetto.jfr.fixture;

import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

@Name("jdk.ThreadEnd")
@Label("Synthetic Thread End")
@Category({"JVM", "Threads"})
public class SyntheticThreadEndEvent extends Event {
}
