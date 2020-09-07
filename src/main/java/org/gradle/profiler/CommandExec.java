package org.gradle.profiler;

import javax.annotation.Nullable;
import java.io.*;
import java.lang.reflect.Field;
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

    protected CommandExec(File directory) {
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
            run(new ProcessBuilder(commandLine), outputStream, null, null).waitForSuccess();
        } catch (RuntimeException e) {
            System.out.print(new String(outputStream.toByteArray()));
            throw e;
        }
        return new String(outputStream.toByteArray());
    }

    public void runAndCollectOutput(File outputFile, Collection<String> commandLine) {
        runAndCollectOutput(outputFile, commandLine.toArray(new String[0]));
    }

    public void runAndCollectOutput(File outputFile, String... commandLine) {
        FileOutputStream outputStream;
        try {
            outputStream = new FileOutputStream(outputFile);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        run(new ProcessBuilder(commandLine), new BufferedOutputStream(outputStream), Logging.detailed(), null).waitForSuccess();
    }

    public void run(ProcessBuilder processBuilder) {
        OutputStream outputStream = Logging.detailed();
        run(processBuilder, outputStream, null, null).waitForSuccess();
    }

    protected RunHandle run(ProcessBuilder processBuilder, OutputStream outputStream, @Nullable OutputStream errorStream, @Nullable InputStream inputStream) {
        if (directory != null) {
            processBuilder.directory(directory);
        }
        String command = processBuilder.command().get(0);
        Logging.detailed().println("Running command " + String.join(" ", processBuilder.command()));
        ByteArrayOutputStream diagnosticOutput = new ByteArrayOutputStream();
        try {
            if (errorStream == null) {
                processBuilder.redirectErrorStream(true);
            }
            Process process = processBuilder.start();
            ExecutorService executor = Executors.newFixedThreadPool(3);
            executor.execute(() -> {
                byte[] buffer = new byte[4096];
                while (true) {
                    if (readStream(process.getInputStream(), outputStream, diagnosticOutput, command, buffer)) break;
                }
            });
            if (errorStream != null) {
                executor.execute(() -> {
                    byte[] buffer = new byte[4096];
                    while (true) {
                        if (readStream(process.getErrorStream(), errorStream, diagnosticOutput, command, buffer)) break;
                    }
                });
            }
            if (inputStream != null) {
                executor.execute(() -> {
                    byte[] buffer = new byte[4096];
                    OutputStream output = process.getOutputStream();
                    while (true) {
                        try {
                            int read = inputStream.read(buffer);
                            output.write(buffer);
                            if (read == -1) {
                                output.flush();
                                output.close();
                                break;
                            }
                        } catch (IOException e) {
                            throw new RuntimeException("Could not write input", e);
                        }
                    }
                });
            }
            return new RunHandle(processBuilder, process, diagnosticOutput, executor);
        } catch (IOException e) {
            throw new RuntimeException(commandErrorMessage(processBuilder, diagnosticOutput), e);
        }
    }

    private boolean readStream(InputStream inputStream, OutputStream outputStream, OutputStream diagnoticStream, String command, byte[] buffer) {
        int nread;
        try {
            nread = inputStream.read(buffer);
        } catch (IOException e) {
            throw new RuntimeException("Could not read input from child process for command '" + command + "'", e);
        }
        if (nread < 0) {
            return true;
        }
        try {
            outputStream.write(buffer, 0, nread);
            diagnoticStream.write(buffer, 0, nread);
        } catch (IOException e) {
            throw new RuntimeException("Could not write output from child process for command '" + command + "'", e);
        }
        return false;
    }

    private String commandErrorMessage(ProcessBuilder processBuilder, ByteArrayOutputStream diagnosticOutput) {
        String message = "Could not run command " + processBuilder.command().stream().collect(Collectors.joining(" "));
        String diagnosticMessage = diagnosticOutput.toString();
        if (!diagnosticMessage.isEmpty()) {
            message += "\nOutput:\n======\n" + diagnosticMessage + "======";
        }
        return message;
    }

    public class RunHandle {
        private final ProcessBuilder processBuilder;
        private final Process process;
        private final ByteArrayOutputStream diagnosticOutput;
        private final ExecutorService executor;

        RunHandle(ProcessBuilder processBuilder, Process process, ByteArrayOutputStream diagnosticOutput, ExecutorService executor) {
            this.processBuilder = processBuilder;
            this.process = process;
            this.diagnosticOutput = diagnosticOutput;
            this.executor = executor;
        }

        public void waitForSuccess() {
            int result = 0;
            Exception failure = null;
            try {
                result = process.waitFor();
            } catch (Exception e) {
                failure = e;
            } finally {
                shutdownExecutor();
            }
            if (failure != null) {
                throw new RuntimeException(commandErrorMessage(processBuilder, diagnosticOutput), failure);
            }
            if (result != 0) {
                throw new RuntimeException(commandErrorMessage(processBuilder, diagnosticOutput));
            }
        }

        public void interrupt() {
            int pid;
            try {
                Field field = process.getClass().getDeclaredField("pid");
                field.setAccessible(true);
                pid = (int) field.get(process);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            run("kill", String.valueOf(pid));
            try {
                process.waitFor();
            } catch (InterruptedException ignore) {
            }
            shutdownExecutor();
        }

        public void kill() {
            process.destroy();
            shutdownExecutor();
        }

        private void shutdownExecutor() {
            try {
                executor.shutdown();
                executor.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
        }
    }
}
