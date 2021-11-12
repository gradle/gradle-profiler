package org.gradle.profiler.studio.tools;

import org.gradle.profiler.CommandExec;
import org.gradle.profiler.studio.LaunchConfiguration;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class StudioLauncher {

    private StudioLauncher() {
    }

    public static CommandExec.RunHandle launchStudio(LaunchConfiguration launchConfiguration, File projectDir) {
        List<String> commandLine = new ArrayList<>(launchConfiguration.getCommandLine());
        commandLine.add(projectDir.getAbsolutePath());
        logLauncherConfiguration(launchConfiguration, commandLine);
        return new CommandExec().inDir(launchConfiguration.studioInstallDir().toFile()).start(commandLine);
    }

    private static void logLauncherConfiguration(LaunchConfiguration launchConfiguration, List<String> commandLine) {
        System.out.println();
        System.out.println("* Java command: " + launchConfiguration.getJavaCommand());
        System.out.println("* Classpath:");
        launchConfiguration.getClassPath().stream().map(entry -> "  " + entry).forEach(System.out::println);
        System.out.println("* System properties:");
        launchConfiguration.getSystemProperties().forEach((key, value) -> System.out.printf("  %s -> %s%n", key, value));
        System.out.println("* Main class: " + launchConfiguration.getMainClass());
        System.out.println("* Android Studio logs can be found at: " + Paths.get(launchConfiguration.getStudioLogsDir().toString(), "idea.log"));
        System.out.printf("* Using command line: %s%n%n", commandLine);
    }

}
