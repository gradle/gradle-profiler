package org.gradle.profiler;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class CommandExec {
    public void run(Collection<String> commandLine) {
        run(commandLine.toArray(new String[commandLine.size()]));
    }

    public void run(String... commandLine) {
        ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
        run(processBuilder);
    }

    public void run(ProcessBuilder processBuilder) {
        String command = processBuilder.command().get(0);
        int result;
        try {
            Process process = processBuilder.redirectErrorStream(true).start();
            ExecutorService executor = Executors.newFixedThreadPool(1);
            executor.execute(() -> {
                InputStream inputStream = process.getInputStream();
                byte[] buffer = new byte[4096];
                while (true) {
                    int nread = 0;
                    try {
                        nread = inputStream.read(buffer);
                    } catch (IOException e) {
                        throw new RuntimeException("Could not read input from child process for command '" + command + "'", e);
                    }
                    if (nread < 0) {
                        break;
                    }
                    Logging.detailed().write(buffer, 0, nread);
                }
            });
            process.getOutputStream().close();
            result = process.waitFor();
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Could not run command " + processBuilder.command().stream().collect(Collectors.joining(" ")), e);
        }
        if (result != 0) {
            throw new RuntimeException("Could not run command " + processBuilder.command().stream().collect(Collectors.joining(" ")));
        }
    }
}
