package org.gradle.profiler.perfetto.jfr.fixture;

import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;
import jdk.jfr.Timestamp;

/**
 * Mirrors the {@code BuildOperationJfrEvent} format proposed in gradle/gradle#38079: no explicit
 * failure flag (an operation failed if either failure field is non-null), and Gradle's own clock
 * captured alongside the JFR timestamps.
 */
@Name("org.gradle.internal.operations.BuildOperation")
@Label("Synthetic Build Operation")
@Category({"Gradle", "Build Operations"})
@StackTrace(false)
public class SyntheticBuildOperationEvent extends Event {
    public long operationId;
    public long parentId;
    public String displayName;
    @Timestamp
    public long gradleStartTime;
    @Timestamp
    public long gradleEndTime;
    public String failureMessage;
    public String failureType;
}
