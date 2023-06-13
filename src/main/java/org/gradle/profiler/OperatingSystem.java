package org.gradle.profiler;

public class OperatingSystem {

    private static final String OS_NAME = System.getProperty("os.name").toLowerCase();
    private static final String OS_ARCH = System.getProperty("os.arch").toLowerCase();
    private static final String OS_VERSION = System.getProperty("os.version").toLowerCase();
    private static final String ID = String.format("%s %s %s", OS_NAME, OS_ARCH, OS_VERSION);

    public static boolean isWindows() {
        return OS_NAME.startsWith("windows");
    }

    public static boolean isMacOS() {
        return OS_NAME.startsWith("mac");
    }

    public static boolean isLinuxX86() {
        return OS_NAME.startsWith("linux") && (OS_ARCH.equals("amd64") || OS_ARCH.equals("x86_64") || OS_ARCH.equals("x86"));
    }

    public static String getId() {
        return ID;
    }
}
