package org.gradle.profiler.studio.launcher;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.gradle.profiler.CommandExec;
import org.gradle.profiler.Logging;
import org.gradle.profiler.studio.tools.StudioSandboxCreator.StudioSandbox;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class LaunchConfiguration {

    private final Path startCommand;
    private final Path studioInstallDir;
    private final String headlessCommand;
    private final List<String> additionalJvmArgs;
    private final StudioSandbox studioSandbox;

    public LaunchConfiguration(
        Path startCommand,
        String headlessCommand,
        Path studioInstallDir,
        List<String> additionalJvmArgs,
        StudioSandbox studioSandbox
    ) {
        this.startCommand = startCommand;
        this.headlessCommand = headlessCommand;
        this.studioInstallDir = studioInstallDir;
        this.additionalJvmArgs = additionalJvmArgs;
        this.studioSandbox = studioSandbox;
    }

    public CommandExec.RunHandle launchStudio(File projectDir) {
        List<String> commandLine = getCommandLine(projectDir);
        logLauncherConfiguration(commandLine);
        Map<String, String> environmentVariables = writeAdditionalJvmArgs();
        return new CommandExec()
            .inDir(studioInstallDir.toFile())
            .environmentVariables(environmentVariables)
            .start(commandLine);
    }

    private List<String> getCommandLine(File projectDir) {
        if (headlessCommand.isEmpty()) {
            return ImmutableList.of(startCommand.toAbsolutePath().toString(), projectDir.getAbsolutePath());
        }
        return ImmutableList.of(startCommand.toAbsolutePath().toString(), headlessCommand, projectDir.getAbsolutePath());
    }

    private void logLauncherConfiguration(List<String> commandLine) {
        System.out.println();
        Logging.startOperation("Starting Android Studio at " + studioInstallDir);
        System.out.println("* Start command: " + startCommand);
        System.out.println("* Additional JVM args:");
        additionalJvmArgs.forEach(arg -> System.out.println("  " + arg));
        System.out.println("* Additional JVM args can be found at: " + studioSandbox.getJvmArgsDir().resolve("idea.vmoptions"));
        System.out.println("* Android Studio logs can be found at: " + studioSandbox.getLogsDir().resolve("idea.log"));
        System.out.printf("* Using command line: %s%n%n", String.join(" ", commandLine));
    }

    private Map<String, String> writeAdditionalJvmArgs() {
        try {
            Path additionJvmArgsFile = studioSandbox.getJvmArgsDir().resolve("idea.vmoptions").toAbsolutePath();
            Files.write(additionJvmArgsFile, additionalJvmArgs);
            return ImmutableMap.<String, String>builder()
                .put("STUDIO_VM_OPTIONS", additionJvmArgsFile.toString())
                .put("IDEA_VM_OPTIONS", additionJvmArgsFile.toString())
                .build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
