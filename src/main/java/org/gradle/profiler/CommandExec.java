package org.gradle.profiler;

import java.io.*;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class CommandExec {
    private final File directory;

    public CommandExec() {
        this.directory = null;
    }

    private CommandExec(File directory) {
        this.directory = directory;
    }

    public CommandExec inDir(File directory) {
        return new CommandExec(directory);
    }

    public void run(Collection<String> commandLine) {
        run(commandLine.toArray(new String[commandLine.size()]));
    }

    public void run(String... commandLine) {
        ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
        run(processBuilder);
    }

    public String runAndCollectOutput(List<String> commandLine) {
        return runAndCollectOutput(commandLine.toArray(new String[commandLine.size()]));
    }

    public String runAndCollectOutput(String... commandLine) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            run(new ProcessBuilder(commandLine), outputStream);
        } catch (RuntimeException e) {
            System.out.print(new String(outputStream.toByteArray()));
            throw e;
        }
        return new String(outputStream.toByteArray());
    }

    public void run(ProcessBuilder processBuilder) {
        OutputStream outputStream = Logging.detailed();
        run(processBuilder, outputStream);
    }

    private void run(ProcessBuilder processBuilder, OutputStream outputStream) {
        if (directory != null) {
            processBuilder.directory(directory);
        }
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
                    try {
                        outputStream.write(buffer, 0, nread);
                    } catch (IOException e) {
                        throw new RuntimeException("Could not write output from child process for command '" + command + "'", e);
                    }
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
