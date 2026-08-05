package org.gradle.profiler.jfr;

import org.gradle.profiler.VersionUtils;

/**
 * JFR profiling that approximates a wall-clock profile: in addition to the standard
 * execution samples, all blocking events (thread park, monitor enter/wait, sleep) and
 * file/socket I/O events are recorded with a zero-duration threshold, and native method
 * samples are enabled. This makes time spent off-CPU visible, at the cost of a larger
 * recording.
 * <p>
 * Useful on platforms where async-profiler is not available, e.g. Windows.
 */
public class JfrWallProfilerFactory extends JfrProfilerFactory {

    @Override
    public String getName() {
        return "jfr-wall";
    }

    @Override
    protected String getDefaultJfcTemplateName() {
        int javaVersion = VersionUtils.getJavaVersion();
        if (javaVersion >= 9 || (!isOracleVm() && javaVersion >= 8)) {
            return "openjdk-wall.jfc";
        }
        throw new IllegalArgumentException("jfr-wall is only supported on OpenJDK since Java 8 and Oracle JDK since Java 9");
    }

}
