package org.gradle.profiler.perfetto.jfr.fixture;

import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

@Name("jdk.ThreadSleep")
@Label("Synthetic Thread Sleep")
@Category({"JVM", "Threads"})
public class SyntheticThreadSleepEvent extends Event {
}
