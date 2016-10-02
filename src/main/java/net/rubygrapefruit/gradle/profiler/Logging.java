package net.rubygrapefruit.gradle.profiler;

import java.io.*;

public class Logging {
    public static void setupLogging() throws FileNotFoundException {
        File logFile = new File("profile.log");
        OutputStream log = new BufferedOutputStream(new FileOutputStream(logFile));
        System.setOut(new PrintStream(new TeeOutputStream(System.out, log)));
    }

    public static void startOperation(String name) {
        System.out.println();
        System.out.println("* " + name);
    }
}
