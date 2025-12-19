package org.gradle.profiler.fixtures.compatibility.gradle

import org.gradle.util.GradleVersion

// copied from org.gradle.integtests.fixtures.executer.DefaultGradleDistribution in gradle/gradle
class DefaultGradleDistribution {
    /**
     * The java version mapped to the first Gradle version that supports it.
     *
     * @see <a href="https://docs.gradle.org/current/userguide/compatibility.html#java_runtime">link</a>
     */
    private static final TreeMap<Integer, String> MAX_SUPPORTED_JAVA_VERSIONS = [
        9 : "4.3",
        10: "4.7",
        11: "5.0",
        12: "5.4", // 5.4 officially added support for JDK 12, but it worked before then.
        13: "6.0",
        14: "6.3",
        15: "6.7",
        16: "7.0",
        17: "7.3",
        18: "7.5",
        19: "7.6",
        20: "8.3",
        21: "8.5",
        22: "8.8",
        23: "8.10",
        24: "8.14",
        25: "9.1.0",
    ]

    /**
     * The java version mapped to the first Gradle version that required it as
     * a minimum for the daemon.
     */
    private static final TreeMap<Integer, String> MIN_SUPPORTED_DAEMON_JAVA_VERSIONS = [
        8 : "5.0",
        17: "9.0",
    ]

    private final GradleVersion version;

    DefaultGradleDistribution(GradleVersion gradleVersion) {
        this.version = gradleVersion;
    }

    static void main(String[] args) {
        println(new DefaultGradleDistribution(GradleVersion.version("9.1.0")).daemonWorksWith(21))
        println(new DefaultGradleDistribution(GradleVersion.version("9.1.0")).daemonWorksWith(17))
        println(new DefaultGradleDistribution(GradleVersion.version("9.1.0")).daemonWorksWith(11))
    }

    boolean daemonWorksWith(int jvmVersion) {
        return jvmVersion >= getMinSupportedDaemonJavaVersion() && jvmVersion <= getMaxSupportedJavaVersion()
    }

    def getMaxSupportedJavaVersion() {
        return findHighestSupportedKey(MAX_SUPPORTED_JAVA_VERSIONS)
            .orElse(8) // Java 8 support was added in Gradle 2.0
    }

    def getMinSupportedDaemonJavaVersion() {
        return findHighestSupportedKey(MIN_SUPPORTED_DAEMON_JAVA_VERSIONS)
            .orElse(7) // Java 7 has been required since Gradle 3.0
    }

    /**
     * Find the highest key such that the corresponding value is the
     * same or newer than the current Gradle version.
     */
    private Optional<Integer> findHighestSupportedKey(NavigableMap<Integer, String> versionMap) {
        return versionMap.descendingMap().entrySet().stream()
            .filter { isSameOrNewer(it.value) }
            .findFirst()
            .map { it.key }
    }

    private boolean isNewer(String otherVersion) {
        version > GradleVersion.version(otherVersion)
    }

    private boolean isSameOrNewer(String otherVersion) {
        return isVersion(otherVersion) || isNewer(otherVersion)
    }

    private boolean isVersion(String otherVersionString) {
        GradleVersion otherVersion = GradleVersion.version(otherVersionString);
        return version == otherVersion || (version.isSnapshot() && version.getBaseVersion() == otherVersion.getBaseVersion());
    }
}
