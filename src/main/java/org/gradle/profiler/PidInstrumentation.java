package org.gradle.profiler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;

class PidInstrumentation extends GeneratedInitScript {
    private final File pidFile;

    PidInstrumentation() throws IOException {
        super();
        pidFile = File.createTempFile("gradle-profiler", "pid");
        pidFile.deleteOnExit();
        generateInitScript();
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
