package org.gradle.profiler;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DefaultVersionInspector implements VersionInspector {

    private final static String bazelHome = System.getenv("BAZEL_HOME");
    private final static String bazelExe = bazelHome == null ? "bazel" : bazelHome + "/bin/bazel";

    public final static VersionInspector BAZEL = new DefaultVersionInspector("bazel", "bazel", "Build label: (.+)", bazelExe, " version");
    public final static VersionInspector BUCK = new DefaultVersionInspector("buck", "buck", "buck version (.+)", "./buckw", "--version");

    private Version cachedVersion;
    private final String name;
    private final String defaultVersion;
    private final String[] commands;
    private final String regex;

    DefaultVersionInspector(String name, String defaultVersion, String regex, String... versionCommands) {
        this.name = name;
        this.defaultVersion = defaultVersion;
        this.regex = regex;
        this.commands = versionCommands;
    }

    public Version getVersion() {
        if (cachedVersion == null) {
            try {
                ProcessBuilder processBuilder = new ProcessBuilder(commands);
                processBuilder.redirectError();
                Process process = processBuilder.start();
                process.waitFor();
                String output = IOUtils.toString(process.getInputStream(), "UTF-8");
                // parse the output
                Pattern pattern = Pattern.compile(regex);
                Matcher matcher = pattern.matcher(output);
                if (matcher.find()) {
                    String version = matcher.group(1);
                    cachedVersion = new Version(version);
                } else {
                    cachedVersion = new Version(defaultVersion);
                }

            } catch (InterruptedException | IOException e) {
                System.err.println("Unable to obtain the " + name + " version");
                e.printStackTrace();
                cachedVersion = new Version(defaultVersion);
            }

        }
        return cachedVersion;
    }
}
