package net.rubygrapefruit.gradle.profiler;

import java.io.*;

public class Logging {
    private static PrintStream originalStdOut = System.out;
    private static PrintStream detail = System.out;

    /**
     * Resets logging to its original state before {@link #setupLogging()} was called.
     */
    public static void resetLogging() {
        if (detail != originalStdOut) {
            System.out.flush();
            System.setOut(originalStdOut);
            detail.close();
            detail = originalStdOut;
        }
    }

    /**
     * Routes System.out to log file.
     */
    public static void setupLogging() throws IOException {
        System.out.flush();
        File logFile = new File("profile.log");
        OutputStream log = new BufferedOutputStream(new FileOutputStream(logFile));
        detail = new PrintStream(log, true);
        System.setOut(new PrintStream(new TeeOutputStream(originalStdOut, detail)));
    }

    /**
     * A stream to write detailed logging messages to.
     */
    public static PrintStream detailed() {
        return detail;
    }

    /**
     * Writes an operation header.
     */
    public static void startOperation(String name) {
        System.out.println();
        System.out.println("* " + name);
    }
}
