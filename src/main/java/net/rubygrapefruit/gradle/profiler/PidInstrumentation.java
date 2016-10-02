package net.rubygrapefruit.gradle.profiler;

import java.io.*;
import java.util.Arrays;
import java.util.List;

class PidInstrumentation {
    private final File initScript;
    private final File pidFile;

    PidInstrumentation() throws IOException {
        initScript = File.createTempFile("gradle-profiler", ".gradle");
        initScript.deleteOnExit();
        pidFile = File.createTempFile("gradle-profiler", "pid");
        pidFile.deleteOnExit();
        generateInitScript();
    }

    private void generateInitScript() throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(initScript))) {
            writer.println("def e");
            writer.println("if (gradleVersion == '2.0') {");
            writer.println("  e = services.get(org.gradle.internal.nativeplatform.ProcessEnvironment)");
            writer.println("} else {");
            writer.println("  e = services.get(org.gradle.internal.nativeintegration.ProcessEnvironment)");
            writer.println("}");
            writer.println("new File(new URI('" + pidFile.toURI() + "')).text = e.pid");
        }
    }

    public List<String> getArgs() {
        return Arrays.asList("-I", initScript.getAbsolutePath());
    }

    public String getPidForLastBuild() throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(pidFile))) {
            return reader.readLine();
        }
    }
}
