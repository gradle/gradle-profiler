package org.gradle.profiler;

import java.io.*;

class PidInstrumentation extends GradleInstrumentation {
    private final File pidFile;

    PidInstrumentation() throws IOException {
        pidFile = File.createTempFile("gradle-profiler", "pid");
        pidFile.deleteOnExit();
    }

    @Override
    protected void generateInitScriptBody(PrintWriter writer) {
        writer.println("org.gradle.trace.pid.PidCollector.collect(gradle, new File(new URI('" + pidFile.toURI() + "')))");
    }

    public String getPidForLastBuild() {
        try {
            try (BufferedReader reader = new BufferedReader(new FileReader(pidFile))) {
                return reader.readLine();
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not read daemon PID from file.", e);
        }
    }
}
