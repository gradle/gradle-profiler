package org.gradle.profiler;

import java.io.*;

class PidInstrumentation extends GeneratedInitScript {
    private final File pidFile;

    PidInstrumentation() throws IOException {
        pidFile = File.createTempFile("gradle-profiler", "pid");
        pidFile.deleteOnExit();
    }

    @Override
    public void writeContents(final PrintWriter writer) {
        writer.println("def e");
        writer.println("if (gradleVersion == '2.0') {");
        writer.println("  e = services.get(org.gradle.internal.nativeplatform.ProcessEnvironment)");
        writer.println("} else {");
        writer.println("  e = services.get(org.gradle.internal.nativeintegration.ProcessEnvironment)");
        writer.println("}");
        writer.println("new File(new URI('" + pidFile.toURI() + "')).text = e.pid");
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
