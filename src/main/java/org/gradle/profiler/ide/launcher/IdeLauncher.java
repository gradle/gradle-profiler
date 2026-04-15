package org.gradle.profiler.ide.launcher;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.gradle.profiler.CommandExec;
import org.gradle.profiler.Logging;
import org.gradle.profiler.ide.IdeType;
import org.gradle.profiler.ide.tools.IdeSandboxCreator.IdeSandbox;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IdeLauncher {

    private final IdeType ideType;
    private final Path startCommand;
    private final Path ideInstallDir;
    private final String headlessCommand;
    private final List<String> additionalJvmArgs;
    private final List<String> ideaProperties;
    private final IdeSandbox ideSandbox;

    public IdeLauncher(
        IdeType ideType,
        Path startCommand,
        String headlessCommand,
        Path ideInstallDir,
        List<String> additionalJvmArgs,
        IdeSandbox ideSandbox,
        List<String> ideaProperties
    ) {
        this.ideType = ideType;
        this.startCommand = startCommand;
        this.headlessCommand = headlessCommand;
        this.ideInstallDir = ideInstallDir;
        this.additionalJvmArgs = additionalJvmArgs;
        this.ideSandbox = ideSandbox;
        this.ideaProperties = ideaProperties;
    }

    public CommandExec.RunHandle launchIde(File projectDir) {
        List<String> commandLine = getCommandLine(projectDir);
        logLauncherConfiguration(commandLine);
        Map<String, String> environmentVariables = new HashMap<>();
        environmentVariables.putAll(writeAdditionalJvmArgs());
        environmentVariables.putAll(writeIdeaProperties());
        return new CommandExec()
            .inDir(ideInstallDir.toFile())
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
        Logging.startOperation("Starting " + ideType.getDisplayName() + " at " + ideInstallDir);
        System.out.println("* Start command: " + startCommand);
        System.out.println("* Additional JVM args:");
        additionalJvmArgs.forEach(arg -> System.out.println("  " + arg));
        System.out.println("* Additional JVM args can be found at: " + ideSandbox.getScenarioOptionsDir().resolve("idea.vmoptions"));
        System.out.println("* IDEA properties:");
        ideaProperties.forEach(property -> System.out.println("  " + property));
        System.out.println("* IDEA properties can be found at: " + ideSandbox.getScenarioOptionsDir().resolve("idea.properties"));
        System.out.println("* " + ideType.getDisplayName() + " logs can be found at: " + ideSandbox.getLogsDir().resolve("idea.log"));
        System.out.printf("* Using command line: %s%n%n", String.join(" ", commandLine));
    }

    private Map<String, String> writeIdeaProperties() {
        try {
            Path ideaPropertiesFile = ideSandbox.getScenarioOptionsDir().resolve("idea.properties").toAbsolutePath();
            Files.write(ideaPropertiesFile, ideaProperties);
            return ImmutableMap.of(ideType.getPropertiesEnvVar(), ideaPropertiesFile.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Map<String, String> writeAdditionalJvmArgs() {
        try {
            Path additionJvmArgsFile = ideSandbox.getScenarioOptionsDir().resolve("idea.vmoptions").toAbsolutePath();
            Files.write(additionJvmArgsFile, additionalJvmArgs);
            return ImmutableMap.of(ideType.getVmOptionsEnvVar(), additionJvmArgsFile.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
