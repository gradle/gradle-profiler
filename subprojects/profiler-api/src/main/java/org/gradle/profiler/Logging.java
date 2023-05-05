package org.gradle.profiler;

import java.io.*;

public class Logging {
    private static PrintStream originalStdOut = System.out;
    private static PrintStream originalStdErr = System.err;
    private static PrintStream detail = System.out;
    private static OutputStream log;

    /**
     * Resets logging to its original state before {@link #setupLogging(File)} was called.
     */
    public static void resetLogging() throws IOException {
        if (System.out != originalStdOut) {
            System.out.flush();
            System.setOut(originalStdOut);
            detail = originalStdOut;
        }
        if (System.err != originalStdErr) {
            System.err.flush();
            System.setErr(originalStdErr);
        }
        if (log != null) {
            log.close();
            log = null;
        }
    }

    /**
     * Routes System.out to log file.
     * @param outputDir
     */
    public static void setupLogging(File outputDir) throws IOException {
        outputDir.mkdirs();
        File logFile = new File(outputDir, "profile.log");
        log = new BufferedOutputStream(new FileOutputStream(logFile));
        detail = new PrintStream(log, true);
        PrintStream output = new PrintStream(new TeeOutputStream(System.out, detail));
        System.setOut(output);
        System.setErr(output);
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
