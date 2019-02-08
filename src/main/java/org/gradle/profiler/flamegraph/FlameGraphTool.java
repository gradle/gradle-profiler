package org.gradle.profiler.flamegraph;

import org.gradle.profiler.CommandExec;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Generates flame graphs from collapsed stacks.
 */
public class FlameGraphTool {
    private final File flamegraphScript;

    private static File createScript(String scriptName) {
        try (InputStream stream = FlameGraphTool.class.getResource(scriptName + ".pl").openStream()) {
            File script = File.createTempFile(scriptName, ".pl");
            Files.copy(stream, script.toPath(), StandardCopyOption.REPLACE_EXISTING);
            script.deleteOnExit();
            script.setExecutable(true);
            return script;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public FlameGraphTool() {
        flamegraphScript = createScript("flamegraph");
    }

    public FlameGraphTool(File flamegraphHome) {
        flamegraphScript = new File(flamegraphHome, "flamegraph.pl");
    }

    public boolean checkInstallation() {
        try {
            new CommandExec().runAndCollectOutput("perl", "-v");
            return true;
        } catch (Exception e) {
            System.out.println("To get flamegraphs, please install perl.");
            return false;
        }
    }

    public void generateFlameGraph(File stacks, File flames, List<String> args) {
        List<String> allArgs = new ArrayList<>();
        allArgs.add("perl");
        allArgs.add(flamegraphScript.getAbsolutePath());
        allArgs.add(stacks.getAbsolutePath());
        allArgs.addAll(args);
        new CommandExec().runAndCollectOutput(flames, allArgs);
    }

    public void generateFlameGraph(File stacks, File flames, String... args) {
        generateFlameGraph(stacks, flames, Arrays.asList(args));
    }

}
