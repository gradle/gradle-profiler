package net.rubygrapefruit.gradle.profiler;

import java.io.*;

public class Logging {
    private static PrintStream detail = System.out;

    public static void setupLogging() throws IOException {
        File logFile = new File("profile.log");
        OutputStream log = new BufferedOutputStream(new FileOutputStream(logFile));
        detail = new PrintStream(log, true);
        System.setOut(new PrintStream(new TeeOutputStream(System.out, detail)));
    }

    /**
     * A stream to write detailed logging messages to.
     */
    public static PrintStream detailed() {
        return detail;
    }

    public static void startOperation(String name) {
        System.out.println();
        System.out.println("* " + name);
    }
}
