package org.gradle.profiler.perfetto.jfr.fixture;

import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

@Name("jdk.ThreadStart")
@Label("Synthetic Thread Start")
@Category({"JVM", "Threads"})
public class SyntheticThreadStartEvent extends Event {
}
