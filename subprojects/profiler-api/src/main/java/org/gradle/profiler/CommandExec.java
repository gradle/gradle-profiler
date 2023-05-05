package org.gradle.profiler;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class CommandExec {
    private final File directory;
    private final Map<String, String> environmentVariables;

    public CommandExec() {
        this.directory = null;
        this.environmentVariables = null;
    }

    protected CommandExec(File directory, Map<String, String> environmentVariables) {
        this.directory = directory;
        this.environmentVariables = environmentVariables;
    }

    public CommandExec inDir(File directory) {
        return new CommandExec(directory, environmentVariables);
    }

    public CommandExec environmentVariables(Map<String, String> environmentVariables) {
        return new CommandExec(directory, environmentVariables);
    }

    public void run(Collection<String> commandLine) {
        start(commandLine).waitForSuccess();
    }

    public RunHandle start(Collection<String> commandLine) {
        return start(commandLine.toArray(new String[commandLine.size()]));
    }

    public void run(String... commandLine) {
        start(commandLine).waitForSuccess();
    }

    public RunHandle start(String... commandLine) {
        ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
        return start(processBuilder);
    }

    public String runAndCollectOutput(List<String> commandLine) {
        return runAndCollectOutput(commandLine.toArray(new String[commandLine.size()]));
    }

    public String runAndCollectOutput(String... commandLine) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            start(new ProcessBuilder(commandLine), outputStream, outputStream::toString, null).waitForSuccess();
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
        OutputStream outputStream = createFileOutputStream(outputFile);
        start(new ProcessBuilder(commandLine), outputStream, () -> "See build log " + outputFile + " for details", null).waitForSuccess();
    }

    private OutputStream createFileOutputStream(File outputFile) {
        try {
            return new FileOutputStream(outputFile);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public void runAndCollectOutput(File outputFile, ProcessBuilder processBuilder) {
        OutputStream outputStream = createFileOutputStream(outputFile);
        start(processBuilder, outputStream, () -> "See build log " + outputFile + " for details", null).waitForSuccess();
    }

    public void run(ProcessBuilder processBuilder) {
        start(processBuilder).waitForSuccess();
    }

    public RunHandle start(ProcessBuilder processBuilder) {
        OutputStream outputStream = Logging.detailed();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        return start(processBuilder, new TeeOutputStream(outputStream, baos), baos::toString, null);
    }

    private RunHandle start(ProcessBuilder processBuilder, OutputStream outputStream, Supplier<String> diagnosticOutput, @Nullable InputStream inputStream) {
        if (directory != null) {
            processBuilder.directory(directory);
        }
        if (environmentVariables != null) {
            processBuilder.environment().putAll(environmentVariables);
        }

        String command = processBuilder.command().get(0);
        Logging.detailed().println("Running command " + String.join(" ", processBuilder.command()));

        try {
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            ExecutorService executor = Executors.newFixedThreadPool(3);
            executor.execute(() -> {
                byte[] buffer = new byte[4096];
                while (true) {
                    if (readStream(process.getInputStream(), outputStream, command, buffer)) {
                        break;
                    }
                }
            });
            executor.execute(() -> {
                byte[] buffer = new byte[4096];
                while (true) {
                    if (readStream(process.getErrorStream(), outputStream, command, buffer)) {
                        break;
                    }
                }
            });
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
            throw new RuntimeException(commandErrorMessage(processBuilder, diagnosticOutput.get()), e);
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
        } finally {
            try {
                outputStream.flush();
            } catch (IOException e) {
                throw new RuntimeException("Could not write output from child process for command '" + command + "'", e);
            }
        }
        return false;
    }

    private static String commandErrorMessage(ProcessBuilder processBuilder, String diagnosticOutput) {
        String message = "Could not run command " + String.join(" ", processBuilder.command());
        if (!diagnosticOutput.isEmpty()) {
            message += "\nOutput:\n======\n" + diagnosticOutput + "======";
        }
        return message;
    }

    public class RunHandle {
        private final ProcessBuilder processBuilder;
        private final Process process;
        private final Supplier<String> diagnosticOutput;
        private final ExecutorService executor;

        RunHandle(ProcessBuilder processBuilder, Process process, Supplier<String> diagnosticOutput, ExecutorService executor) {
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
                throw new RuntimeException(commandErrorMessage(processBuilder, diagnosticOutput.get()), failure);
            }
            if (result != 0) {
                throw new RuntimeException(commandErrorMessage(processBuilder, diagnosticOutput.get()));
            }
        }

        public void waitForSuccess(long timeout, TimeUnit unit) throws ExecutionException, InterruptedException, TimeoutException {
            Future<?> future = executor.submit((Runnable) this::waitForSuccess);
            future.get(timeout, unit);
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
            // On Windows subprocesses are not automatically
            // killed with parent, so let's do that manually
            destroyDescendants();
            process.destroy();
            shutdownExecutor();
        }

        private void destroyDescendants() {
            if (VersionUtils.getJavaVersion() < 9) {
                // ProcessHandle API is available only from JDK9
                return;
            }
            try {
                @SuppressWarnings("unchecked")
                Stream<Object> descendants = (Stream<Object>) Process.class.getMethod("descendants").invoke(process);
                long parentPid = (long) Process.class.getMethod("pid").invoke(process);
                Method pidMethod = Class.forName("java.lang.ProcessHandle").getMethod("pid");
                Method destroyMethod = Class.forName("java.lang.ProcessHandle").getMethod("destroy");
                descendants.forEach(descendant -> destroyDescendant(parentPid, descendant, pidMethod, destroyMethod));
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }

        private void destroyDescendant(long parentPid, Object child, Method pidMethod, Method destroyMethod) {
            try {
                long pid = (long) pidMethod.invoke(child);
                boolean success = (boolean) destroyMethod.invoke(child);
                String successOrFailure = success ? "Successfully" : "Unsuccessfully";
                Logging.detailed().printf("%s requested termination of descendant '%d' of parent '%d'. Parent will be terminated after that.%n", successOrFailure, pid, parentPid);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
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
