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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StudioLauncher {

    private final Path startCommand;
    private final Path studioInstallDir;
    private final String headlessCommand;
    private final List<String> additionalJvmArgs;
    private final List<String> ideaProperties;
    private final StudioSandbox studioSandbox;

    public StudioLauncher(
        Path startCommand,
        String headlessCommand,
        Path studioInstallDir,
        List<String> additionalJvmArgs,
        StudioSandbox studioSandbox,
        List<String> ideaProperties
    ) {
        this.startCommand = startCommand;
        this.headlessCommand = headlessCommand;
        this.studioInstallDir = studioInstallDir;
        this.additionalJvmArgs = additionalJvmArgs;
        this.studioSandbox = studioSandbox;
        this.ideaProperties = ideaProperties;
    }

    public CommandExec.RunHandle launchStudio(File projectDir) {
        List<String> commandLine = getCommandLine(projectDir);
        logLauncherConfiguration(commandLine);
        Map<String, String> environmentVariables = new HashMap<>();
        environmentVariables.putAll(writeAdditionalJvmArgs());
        environmentVariables.putAll(writeIdeaProperties());
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
        System.out.println("* Additional JVM args can be found at: " + studioSandbox.getScenarioOptionsDir().resolve("idea.vmoptions"));
        System.out.println("* IDEA properties:");
        ideaProperties.forEach(property -> System.out.println("  " + property));
        System.out.println("* IDEA properties can be found at: " + studioSandbox.getScenarioOptionsDir().resolve("idea.properties"));
        System.out.println("* Android Studio logs can be found at: " + studioSandbox.getLogsDir().resolve("idea.log"));
        System.out.printf("* Using command line: %s%n%n", String.join(" ", commandLine));
    }

    private Map<String, String> writeIdeaProperties() {
        try {
            Path ideaPropertiesFile = studioSandbox.getScenarioOptionsDir().resolve("idea.properties").toAbsolutePath();
            Files.write(ideaPropertiesFile, ideaProperties);
            return ImmutableMap.<String, String>builder()
                .put("STUDIO_PROPERTIES", ideaPropertiesFile.toString())
                .put("IDEA_PROPERTIES", ideaPropertiesFile.toString())
                .build();

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Map<String, String> writeAdditionalJvmArgs() {
        try {
            Path additionJvmArgsFile = studioSandbox.getScenarioOptionsDir().resolve("idea.vmoptions").toAbsolutePath();
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
