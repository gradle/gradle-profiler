package org.gradle.profiler.mutations;

import java.io.IOException;
import java.util.List;

public class ProcessBuilderCommandInvoker implements CommandInvoker {

    @Override
    public int execute(List<String> command) {
        try {
            if (command == null || command.isEmpty()) {
                throw new IllegalArgumentException(
                    String.format("command cannot be null or empty, was %s", command));
            }
            ProcessBuilder processBuilder = new ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.INHERIT);
            Process process = processBuilder.start();
            return process.waitFor();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
