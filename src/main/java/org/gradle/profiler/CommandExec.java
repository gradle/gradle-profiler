package org.gradle.profiler;

import org.gradle.api.Nullable;

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

    public void runAndCollectOutput(File outputFile, String... commandLine) {
        FileOutputStream outputStream;
        try {
            outputStream = new FileOutputStream(outputFile);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        run(new ProcessBuilder(commandLine), outputStream, Logging.detailed(), null).waitForSuccess();
    }

    public void runAndCollectOutput(File outputFile, File inputFile, String... commandLine) {
        FileOutputStream outputStream;
        FileInputStream inputStream;
        try {
            outputStream = new FileOutputStream(outputFile);
            inputStream = new FileInputStream(inputFile);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        run(new ProcessBuilder(commandLine), outputStream, Logging.detailed(), inputStream).waitForSuccess();
    }

    public void run(ProcessBuilder processBuilder) {
        OutputStream outputStream = Logging.detailed();
        run(processBuilder, outputStream, null, null).waitForSuccess();
    }

    public RunHandle runBackgrounded(String... commandLine) {
        OutputStream outputStream = Logging.detailed();
        return run(new ProcessBuilder(commandLine), outputStream, null, null);
    }

    protected RunHandle run(ProcessBuilder processBuilder, OutputStream outputStream, @Nullable OutputStream errorStream, @Nullable InputStream inputStream) {
        if (directory != null) {
            processBuilder.directory(directory);
        }
        String command = processBuilder.command().get(0);
        try {
            if (errorStream == null) {
                processBuilder.redirectErrorStream(true);
            }
            Process process = processBuilder.start();
            ExecutorService executor = Executors.newFixedThreadPool(3);
            executor.execute(() -> {
                byte[] buffer = new byte[4096];
                while (true) {
                    if (readStream(process.getInputStream(), outputStream, command, buffer)) break;
                }
            });
            if (errorStream != null) {
                executor.execute(() -> {
                    byte[] buffer = new byte[4096];
                    while (true) {
                        if (readStream(process.getErrorStream(), errorStream, command, buffer)) break;
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
            return new RunHandle(processBuilder, process, executor);
        } catch (IOException e) {
            throw new RuntimeException(commandErrorMessage(processBuilder), e);
        }
    }

    private boolean readStream(InputStream inputStream, OutputStream outputStream, String command, byte[] buffer) {
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
        } catch (IOException e) {
            throw new RuntimeException("Could not write output from child process for command '" + command + "'", e);
        }
        return false;
    }

    private String commandErrorMessage(ProcessBuilder processBuilder) {
        return "Could not run command " + processBuilder.command().stream().collect(Collectors.joining(" "));
    }

    public class RunHandle {
        private final ProcessBuilder processBuilder;
        private final Process process;
        private final ExecutorService executor;

        RunHandle(ProcessBuilder processBuilder, Process process, ExecutorService executor) {
            this.processBuilder = processBuilder;
            this.process = process;
            this.executor = executor;
        }

        public void waitForSuccess() {
            int result;
            try {
                result = process.waitFor();
            } catch (Exception e) {
                throw new RuntimeException(commandErrorMessage(processBuilder), e);
            } finally {
                shutdownExecutor();
            }
            if (result != 0) {
                throw new RuntimeException(commandErrorMessage(processBuilder) + ". Exited with result " + result);
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
